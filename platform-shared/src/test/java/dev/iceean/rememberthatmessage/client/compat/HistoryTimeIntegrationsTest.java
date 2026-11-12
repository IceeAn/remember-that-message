package dev.iceean.rememberthatmessage.client.compat;

import dev.iceean.rememberthatmessage.network.HistoryProtocol;
import dev.iceean.rememberthatmessage.core.MessageKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryTimeIntegrationsTest {
    @Test void multipleAdaptersRestoreTheirScopesAroundOneInsertion() {
        var events = new ArrayList<String>();
        var first = scopedAdapter("first", events);
        var second = scopedAdapter("second", events);
        var manager = new HistoryTimeIntegrations(List.of(first, second));
        var item = new HistoryProtocol.Item(1, MessageKind.JOIN, Instant.EPOCH, Component.literal("history"), null, null);
        manager.display(item, () -> events.add("insert"));
        assertEquals(List.of("first enter", "second enter", "insert", "second exit", "first exit"), events);
        verify(first).display(eq(item), any());
        verify(second).display(eq(item), any());
    }

    @Test void displayFailureUnwindsScopesWithoutRetryingInsertion() {
        var events = new ArrayList<String>();
        var manager = new HistoryTimeIntegrations(List.of(scopedAdapter("first", events), scopedAdapter("second", events)));
        var failure = new IllegalStateException("downstream failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> manager.display(null, () -> {
            events.add("insert"); throw failure;
        })));
        assertEquals(List.of("first enter", "second enter", "insert", "second exit", "first exit"), events);
    }

    @Test void unavailableAdapterDoesNotBlockOtherAdaptersOrBasicDisplay() {
        var events = new ArrayList<String>();
        var absent = mock(HistoryTimeAdapter.class);
        when(absent.state()).thenReturn("absent");
        var failed = mock(HistoryTimeAdapter.class);
        when(failed.state()).thenReturn("runtime_error");
        var active = scopedAdapter("active", events);
        var manager = new HistoryTimeIntegrations(List.of(absent, failed, active));
        assertTrue(manager.hasUnavailableIntegration()); assertTrue(manager.hasEnabledIntegration());
        manager.display(null, () -> events.add("insert"));
        assertEquals(List.of("active enter", "insert", "active exit"), events);
        verify(absent, never()).display(any(), any());
        verify(failed, never()).display(any(), any());

        manager = new HistoryTimeIntegrations(List.of(absent));
        assertFalse(manager.hasUnavailableIntegration()); assertFalse(manager.hasEnabledIntegration());
        events.clear(); manager.display(null, () -> events.add("insert"));
        assertEquals(List.of("insert"), events);
        events.clear(); new HistoryTimeIntegrations(List.of()).display(null, () -> events.add("insert"));
        assertEquals(List.of("insert"), events);
    }

    @Test void lifecycleAndFailureReportsRemainSpecificToEachAdapter() {
        var first = mock(HistoryTimeAdapter.class);
        var second = mock(HistoryTimeAdapter.class);
        var callbacks = new ArrayList<Consumer<String>>();
        for (var adapter : List.of(first, second)) {
            doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(adapter).onFailure(any());
        }
        var manager = new HistoryTimeIntegrations(List.of(first, second));
        var failedAdapters = new ArrayList<HistoryTimeAdapter>();
        var failures = new ArrayList<String>();
        manager.onFailure((adapter, failure) -> { failedAdapters.add(adapter); failures.add(failure); });
        callbacks.get(1).accept("second failed"); callbacks.get(0).accept("first failed");
        assertEquals(List.of(second, first), failedAdapters);
        assertEquals(List.of("second failed", "first failed"), failures);
        manager.resetConnection();
        verify(first).resetConnection(); verify(second).resetConnection();
    }

    private static HistoryTimeAdapter scopedAdapter(String name, List<String> events) {
        var adapter = mock(HistoryTimeAdapter.class);
        when(adapter.enabled()).thenReturn(true);
        doAnswer(call -> {
            events.add(name + " enter");
            try { call.getArgument(1, Runnable.class).run(); }
            finally { events.add(name + " exit"); }
            return null;
        }).when(adapter).display(any(), any());
        return adapter;
    }
}
