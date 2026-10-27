package io.github.iceean.serverchathistory.storage;

import io.github.iceean.serverchathistory.core.HistorySnapshot;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** A single writer with coalescing saves and non-coalescible clear/flush barriers. */
public final class AsyncHistoryWriter implements AutoCloseable {
    private record Job(HistorySnapshot snapshot, boolean reset, CompletableFuture<Void> completion) { }
    private final HistoryStore store;
    private final Consumer<Exception> errors;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "server-chat-history-io");
        thread.setDaemon(true);
        return thread;
    });
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private boolean running;
    private boolean writing;
    private boolean closed;
    private boolean resetRequired;
    private volatile String lastFailure;

    public AsyncHistoryWriter(HistoryStore store, Consumer<Exception> errors) { this.store = store; this.errors = errors; }
    public String lastFailure() { return lastFailure; }
    public synchronized int pendingJobs() { return jobs.size() + (writing ? 1 : 0); }

    public synchronized void save(HistorySnapshot snapshot) {
        ensureOpen();
        if (!jobs.isEmpty() && jobs.peekLast().completion() == null) jobs.removeLast();
        jobs.addLast(new Job(snapshot, false, null));
        start();
    }

    public synchronized CompletableFuture<Void> clear(HistorySnapshot snapshot) {
        ensureOpen();
        jobs.removeIf(job -> job.completion() == null);
        CompletableFuture<Void> done = new CompletableFuture<>();
        jobs.addLast(new Job(snapshot, true, done));
        start();
        return done;
    }

    public synchronized CompletableFuture<Void> flush(HistorySnapshot snapshot) {
        ensureOpen();
        CompletableFuture<Void> done = new CompletableFuture<>();
        jobs.addLast(new Job(snapshot, false, done));
        start();
        return done;
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("History writer is closed"); }
    private void start() { if (!running) { running = true; executor.execute(this::drain); } }

    private void drain() {
        while (true) {
            Job job;
            synchronized (this) {
                job = jobs.pollFirst();
                if (job == null) { running = false; return; }
                writing = true;
            }
            try {
                resetRequired |= job.reset();
                if (resetRequired) store.reset(job.snapshot()); else store.save(job.snapshot());
                resetRequired = false;
                lastFailure = null;
                if (job.completion() != null) job.completion().complete(null);
            } catch (Exception failure) {
                boolean firstFailure = lastFailure == null;
                lastFailure = failure.toString();
                if (firstFailure) errors.accept(failure);
                if (job.completion() != null) job.completion().completeExceptionally(failure);
            } finally {
                synchronized (this) { writing = false; }
            }
        }
    }

    @Override public void close() {
        synchronized (this) { closed = true; }
        executor.shutdown();
        try {
            // No server/world objects are accessed by the writer while shutdown waits.
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) { }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            lastFailure = interrupted.toString();
            errors.accept(interrupted);
        }
    }
}
