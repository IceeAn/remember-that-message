package dev.iceean.rememberthatmessage.client;

import com.mojang.text2speech.Narrator;
import dev.iceean.rememberthatmessage.config.ClientConfig;
import dev.iceean.rememberthatmessage.core.MessageKind;
import dev.iceean.rememberthatmessage.network.HistoryProtocol;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objenesis.ObjenesisStd;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryMessageDisplayTest {
    private static HistoryProtocol.Item item(MessageKind kind) {
        return new HistoryProtocol.Item(1, kind, Instant.EPOCH, Component.literal("historic message"),
                kind == MessageKind.CHAT ? UUID.randomUUID() : null, kind == MessageKind.CHAT ? "Alice" : null);
    }

    @ParameterizedTest @EnumSource(ChatRestriction.class)
    void actualClientRestrictionsRejectPlayerHistoryBeforeAnySideEffects(ChatRestriction restriction) {
        var abilities = new ChatAbilities.Builder().addRestriction(restriction).build();
        assertFalse(abilities.canReceivePlayerMessages());
        boolean systemAllowed = restriction != ChatRestriction.CHAT_AND_COMMANDS_DISABLED_BY_OPTIONS;
        assertEquals(systemAllowed, abilities.canReceiveSystemMessages());
        assertFalse(HistoryMessageDisplay.allowed(MessageKind.CHAT, abilities, false));
        HistoryMessageDisplay.display(item(MessageKind.CHAT), abilities, false, false,
                ignored -> fail("Adapter must not run"), ignored -> fail("HUD must not run"), ignored -> fail("Narrator must not run"));

        for (var kind : MessageKind.values()) if (kind != MessageKind.CHAT) {
            assertEquals(systemAllowed, HistoryMessageDisplay.allowed(kind, abilities, false));
            var calls = new ArrayList<String>();
            HistoryMessageDisplay.display(item(kind), abilities, false, false, Runnable::run,
                    ignored -> calls.add("display"), ignored -> calls.add("narrate"));
            assertEquals(systemAllowed ? List.of("display", "narrate") : List.of(), calls);
        }
    }

    @ParameterizedTest @EnumSource(MessageKind.class)
    void defaultSkipsNarrationButStillDisplaysEveryKind(MessageKind kind) {
        var message = item(kind); var displayed = new ArrayList<Component>();
        assertTrue(HistoryMessageDisplay.allowed(kind, ChatAbilities.NO_RESTRICTIONS, false));
        HistoryMessageDisplay.display(message, ChatAbilities.NO_RESTRICTIONS, false,
                ClientConfig.DEFAULT.skipHistoryNarration(), Runnable::run, displayed::add,
                ignored -> fail("Default must not submit speech"));
        assertEquals(List.of(message.content()), displayed);
    }

    @Test void deniedSystemMessagesAndBlockedSendersDoNotEnterAdapterOrNarrator() {
        var denied = mock(ChatAbilities.class);
        when(denied.canReceivePlayerMessages()).thenReturn(true);
        assertFalse(HistoryMessageDisplay.allowed(MessageKind.CHAT, ChatAbilities.NO_RESTRICTIONS, true));
        assertFalse(HistoryMessageDisplay.allowed(MessageKind.CHAT, denied, false));
        for (var kind : MessageKind.values())
            HistoryMessageDisplay.display(item(kind), denied, false, false,
                    ignored -> fail(), ignored -> fail(), ignored -> fail());
        HistoryMessageDisplay.display(item(MessageKind.CHAT), ChatAbilities.NO_RESTRICTIONS, true, false,
                ignored -> fail(), ignored -> fail(), ignored -> fail());
    }

    @Test void narrationRunsOnceAfterAdapterRestoresItsScope() {
        var calls = new ArrayList<String>(); var message = item(MessageKind.CHAT);
        HistoryMessageDisplay.display(message, ChatAbilities.NO_RESTRICTIONS, false, false,
                insert -> { calls.add("enter"); insert.run(); calls.add("restore"); },
                content -> { assertSame(message.content(), content); calls.add("display"); },
                content -> { assertSame(message.content(), content); calls.add("narrate"); });
        assertEquals(List.of("enter", "display", "restore", "narrate"), calls);
    }

    @Test void failedDisplayDoesNotRetryOrNarrate() {
        int[] attempts = {0};
        Consumer<Runnable> adapter = insert -> assertThrows(IllegalStateException.class, insert::run);
        HistoryMessageDisplay.display(item(MessageKind.CHAT), ChatAbilities.NO_RESTRICTIONS, false, false, adapter,
                ignored -> { attempts[0]++; throw new IllegalStateException("Display failed"); }, ignored -> fail());
        assertEquals(1, attempts[0]);
    }

    @Test void suppressedAdapterCallbackDoesNotNarrate() {
        HistoryMessageDisplay.display(item(MessageKind.CHAT), ChatAbilities.NO_RESTRICTIONS, false, false,
                ignored -> { }, ignored -> fail(), ignored -> fail());
    }

    @Test void narratorFailureDoesNotEnterAdapterFailureHandlingOrRepeatDisplay() {
        int[] attempts = {0};
        assertThrows(IllegalStateException.class, () -> HistoryMessageDisplay.display(item(MessageKind.CHAT),
                ChatAbilities.NO_RESTRICTIONS, false, false, insert -> assertDoesNotThrow(insert::run),
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
        var volume = allocator.newInstance(OptionInstance.class);
        setField(OptionInstance.class, volume, "value", 1.0D);
        setField(Options.class, options, "soundSourceVolumes", Map.of(SoundSource.VOICE, volume, SoundSource.MASTER, volume));
        setField(Minecraft.class, minecraft, "options", options);
        var narrator = allocator.newInstance(GameNarrator.class);
        var backend = mock(Narrator.class);
        setField(GameNarrator.class, narrator, "minecraft", minecraft);
        setField(GameNarrator.class, narrator, "narrator", backend);

        HistoryMessageDisplay.display(item(MessageKind.CHAT), ChatAbilities.NO_RESTRICTIONS, false, false,
                Runnable::run, ignored -> { }, narrator::saySystemChatQueued);
        if (status == NarratorStatus.OFF) verifyNoInteractions(backend);
        else verify(backend).say("historic message", false, 1.0F);
        assertEquals(status, options.narrator().get());
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
