package dev.iceean.rememberthatmessage.client.compat;

import dev.iceean.rememberthatmessage.network.ProtocolData;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.BiConsumer;

/** Keeps display, status and connection lifecycle independent of specific time mods. */
public final class HistoryTimeIntegrations {
    private final List<HistoryTimeAdapter> adapters;

    public HistoryTimeIntegrations(List<HistoryTimeAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }
    public static HistoryTimeIntegrations createDefault() {
        return new HistoryTimeIntegrations(List.of(new ChatPatchesAdapter(), ChatToolsAdapter.INSTANCE));
    }
    public List<HistoryTimeAdapter> adapters() { return adapters; }
    public void onFailure(BiConsumer<HistoryTimeAdapter, String> callback) {
        adapters.forEach(adapter -> adapter.onFailure(failure -> callback.accept(adapter, failure)));
    }
    public void resetConnection() { adapters.forEach(HistoryTimeAdapter::resetConnection); }
    public boolean hasUnavailableIntegration() {
        return adapters.stream().anyMatch(adapter -> !adapter.enabled() && !adapter.state().equals("absent"));
    }
    public boolean hasEnabledIntegration() { return adapters.stream().anyMatch(HistoryTimeAdapter::enabled); }
    public void display(ProtocolData.Item<Component> item, Runnable display) { display(0, item, display); }
    private void display(int index, ProtocolData.Item<Component> item, Runnable display) {
        while (index < adapters.size() && !adapters.get(index).enabled()) index++;
        if (index == adapters.size()) { display.run(); return; }
        int next = index + 1;
        adapters.get(index).display(item, () -> display(next, item, display));
    }
}
