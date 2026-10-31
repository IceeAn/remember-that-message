package io.github.iceean.serverchathistory.client;

import com.mojang.text2speech.Narrator;
import io.github.iceean.serverchathistory.config.ClientConfig;
import io.github.iceean.serverchathistory.core.MessageKind;
import io.github.iceean.serverchathistory.network.HistoryProtocol;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.chat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objenesis.ObjenesisStd;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryMessageDisplayTest {
    @BeforeAll static void initializeChatStatusWithoutStartingMinecraft() throws Exception {
        var allocator = new ObjenesisStd();
        var previous = Minecraft.getInstance();
        var client = allocator.newInstance(Minecraft.class);
        var options = allocator.newInstance(Options.class);
        var key = allocator.newInstance(KeyMapping.class);
        setField(KeyMapping.class, key, "name", "key.chat");
        setField(Options.class, options, "keyChat", key);
        setField(Minecraft.class, client, "options", options);
        setField(Minecraft.class, null, "instance", client);
        try { Class.forName("net.minecraft.client.Minecraft$ChatStatus"); }
        finally { setField(Minecraft.class, null, "instance", previous); }
    }

    private static HistoryProtocol.Item item(MessageKind kind) {
        return new HistoryProtocol.Item(1, kind, Instant.EPOCH, Component.literal("historic message"),
                kind == MessageKind.CHAT ? UUID.randomUUID() : null, kind == MessageKind.CHAT ? "Alice" : null);
    }

    @ParameterizedTest @EnumSource(Minecraft.ChatStatus.class)
    void launcherAndProfileRestrictionsRespectTheNativeLocalServerRule(Minecraft.ChatStatus status) {
        for (boolean local : List.of(false, true)) {
            boolean expected = status == Minecraft.ChatStatus.ENABLED
                    || local && (status == Minecraft.ChatStatus.DISABLED_BY_LAUNCHER || status == Minecraft.ChatStatus.DISABLED_BY_PROFILE);
            assertEquals(expected, HistoryMessageDisplay.playerAllowed(net.minecraft.world.entity.player.ChatVisiblity.FULL, status, local));
            assertFalse(HistoryMessageDisplay.playerAllowed(net.minecraft.world.entity.player.ChatVisiblity.SYSTEM, status, local));
            assertFalse(HistoryMessageDisplay.playerAllowed(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN, status, local));
            var calls = new ArrayList<String>();
            HistoryMessageDisplay.display(item(MessageKind.CHAT), true, expected, false, false, Runnable::run,
                    ignored -> calls.add("display"), ignored -> calls.add("narrate"));
            assertEquals(expected ? List.of("display", "narrate") : List.of(), calls);
            calls.clear();
            HistoryMessageDisplay.display(item(MessageKind.JOIN), true, expected, false, false, Runnable::run,
                    ignored -> calls.add("display"), ignored -> calls.add("narrate"));
            assertEquals(List.of("display", "narrate"), calls);
        }
    }

    @ParameterizedTest @EnumSource(MessageKind.class)
    void defaultSkipsNarrationButStillDisplaysEveryKind(MessageKind kind) {
        var message = item(kind); var displayed = new ArrayList<Component>();
        HistoryMessageDisplay.display(message, true, true, false,
                ClientConfig.DEFAULT.skipHistoryNarration(), Runnable::run, displayed::add,
                ignored -> fail("Default must not submit speech"));
        assertEquals(List.of(message.content()), displayed);
    }

    @Test void deniedSystemMessagesAndBlockedSendersDoNotEnterAdapterOrNarrator() {
        for (var kind : MessageKind.values())
            HistoryMessageDisplay.display(item(kind), false, true, false, false,
                    ignored -> fail(), ignored -> fail(), ignored -> fail());
        HistoryMessageDisplay.display(item(MessageKind.CHAT), true, true, true, false,
                ignored -> fail(), ignored -> fail(), ignored -> fail());
    }

    @Test void narrationRunsOnceAfterAdapterRestoresItsScope() {
        var calls = new ArrayList<String>(); var message = item(MessageKind.CHAT);
        HistoryMessageDisplay.display(message, true, true, false, false,
                insert -> { calls.add("enter"); insert.run(); calls.add("restore"); },
                content -> { assertSame(message.content(), content); calls.add("display"); },
                content -> { assertSame(message.content(), content); calls.add("narrate"); });
        assertEquals(List.of("enter", "display", "restore", "narrate"), calls);
    }

    @Test void failedDisplayDoesNotRetryOrNarrate() {
        int[] attempts = {0};
        Consumer<Runnable> adapter = insert -> assertThrows(IllegalStateException.class, insert::run);
        HistoryMessageDisplay.display(item(MessageKind.CHAT), true, true, false, false, adapter,
                ignored -> { attempts[0]++; throw new IllegalStateException("Display failed"); }, ignored -> fail());
        assertEquals(1, attempts[0]);
    }

    @Test void suppressedAdapterCallbackDoesNotNarrate() {
        HistoryMessageDisplay.display(item(MessageKind.CHAT), true, true, false, false,
                ignored -> { }, ignored -> fail(), ignored -> fail());
    }

    @Test void narratorFailureDoesNotEnterAdapterFailureHandlingOrRepeatDisplay() {
        int[] attempts = {0};
        assertThrows(IllegalStateException.class, () -> HistoryMessageDisplay.display(item(MessageKind.CHAT),
                true, true, false, false, insert -> assertDoesNotThrow(insert::run),
                ignored -> attempts[0]++, ignored -> { throw new IllegalStateException("Narrator failed"); }));
        assertEquals(1, attempts[0]);
    }

    @ParameterizedTest @EnumSource(NarratorStatus.class)
    void originalNarratorSettingRemainsAuthoritativeWithoutInitializingSpeechDevice(NarratorStatus status) throws Exception {
        // Bypass game/audio constructors; exercise the real GameNarrator method with a fake speech backend.
        var allocator = new ObjenesisStd();
        var minecraft = allocator.newInstance(Minecraft.class);
        var options = allocator.newInstance(Options.class);
        var option = allocator.newInstance(OptionInstance.class);
        setField(OptionInstance.class, option, "value", status);
        setField(Options.class, options, "narrator", option);
        setField(Minecraft.class, minecraft, "options", options);
        var narrator = allocator.newInstance(GameNarrator.class);
        var backend = mock(Narrator.class);
        setField(GameNarrator.class, narrator, "minecraft", minecraft);
        setField(GameNarrator.class, narrator, "narrator", backend);

        HistoryMessageDisplay.display(item(MessageKind.CHAT), true, true, false, false,
                Runnable::run, ignored -> { }, narrator::say);
        if (!status.shouldNarrateSystem()) verifyNoInteractions(backend);
        else verify(backend).say("historic message", false);
        assertEquals(status, options.narrator().get());
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
