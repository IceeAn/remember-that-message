package io.github.iceean.serverchathistory.minecraft;

import com.mojang.authlib.GameProfile;
import io.github.iceean.serverchathistory.core.HistoryEntry;
import io.github.iceean.serverchathistory.mixin.PlayerInfoPacketAccessor;
import java.util.*;
import java.util.function.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.world.level.GameType;

/** A bounded, recipient-only change to chat validators, restored even if replay fails. */
public final class PlayerInfoLease {
    @FunctionalInterface public interface PacketFactory {
        ClientboundPlayerInfoUpdatePacket create(EnumSet<Action> actions, List<Entry> entries);
    }
    private final Function<UUID, Entry> online;
    private final Consumer<Packet<?>> send;
    private final PacketFactory packets;

    public PlayerInfoLease(Function<UUID, Entry> online, Consumer<Packet<?>> send, PacketFactory packets) {
        this.online = online;
        this.send = send;
        this.packets = packets;
    }

    public static ClientboundPlayerInfoUpdatePacket packet(EnumSet<Action> actions, List<Entry> entries) {
        var packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        ((PlayerInfoPacketAccessor) packet).serverChatHistory$setEntries(List.copyOf(entries));
        return packet;
    }

    public void run(Collection<HistoryEntry.Chat> senders, Runnable replay) {
        var temporary = new ArrayList<UUID>();
        var restore = new ArrayList<Entry>();
        var seen = new HashSet<UUID>();
        try {
            for (var chat : senders) {
                if (!seen.add(chat.senderId())) continue;
                var current = online.apply(chat.senderId());
                var actions = EnumSet.of(Action.INITIALIZE_CHAT);
                if (current == null) {
                    actions.add(Action.ADD_PLAYER);
                    temporary.add(chat.senderId());
                } else restore.add(current);
                var info = new Entry(chat.senderId(), new GameProfile(chat.senderId(), chat.senderName()),
                        false, 0, GameType.SURVIVAL, null, false, 0, null);
                send.accept(packets.create(actions, List.of(info)));
            }
            replay.run();
        } finally {
            try {
                if (!restore.isEmpty()) send.accept(packets.create(EnumSet.of(Action.INITIALIZE_CHAT), restore));
            } finally {
                if (!temporary.isEmpty()) send.accept(new ClientboundPlayerInfoRemovePacket(temporary));
            }
        }
    }
}
