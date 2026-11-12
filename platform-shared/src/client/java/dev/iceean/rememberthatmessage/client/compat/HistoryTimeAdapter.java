package dev.iceean.rememberthatmessage.client.compat;

import dev.iceean.rememberthatmessage.network.ProtocolData;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Optional integration scoped to one history insertion; never retry after insertion starts. */
public interface HistoryTimeAdapter {
    String id();
    String name();
    String version();
    String state();
    String failure();
    boolean enabled();
    void onFailure(Consumer<String> callback);
    void resetConnection();
    void display(ProtocolData.Item<Component> item, Runnable display);
}
