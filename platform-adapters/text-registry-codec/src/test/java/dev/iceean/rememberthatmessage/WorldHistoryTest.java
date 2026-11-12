package dev.iceean.rememberthatmessage;

import com.mojang.serialization.Lifecycle;
import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.minecraft.GameTestPlayers;
import dev.iceean.rememberthatmessage.storage.JsonSupport;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.storage.LevelResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldHistoryTest {
    @TempDir Path directory;
    private static RegistryAccess.Frozen registries;
    private WorldHistory history;
    private MinecraftServer server;
    private Path file;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var types = new MappedRegistry<>(Registries.CHAT_TYPE, Lifecycle.stable());
        types.register(ChatType.CHAT, new ChatType(ChatTypeDecoration.withSender("chat.type.text"),
                ChatTypeDecoration.withSender("chat.type.text.narrate")), RegistrationInfo.BUILT_IN);
        registries = new RegistryAccess.ImmutableRegistryAccess(List.of(types.freeze())).freeze();
    }

    @BeforeEach void createHistory() throws Exception {
        server = mock(MinecraftServer.class);
        when(server.registryAccess()).thenReturn(registries);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(directory);
        when(server.enforceSecureProfile()).thenReturn(true);
        history = new WorldHistory(server, directory.resolve("config.json"));
        file = directory.resolve("data/remember_that_message/history.json");
        awaitWrites();
    }

    @AfterEach void closeHistory() { if (history != null) history.close(); }

    private void awaitWrites() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (history.pendingWrites() > 0) {
            assertTrue(System.nanoTime() < deadline, "Writer did not drain");
            Thread.sleep(1);
        }
        assertFalse(history.storageError());
    }

    private HistorySnapshot saved() throws Exception {
        return JsonSupport.GSON.fromJson(Files.readString(file), dev.iceean.rememberthatmessage.storage.HistoryFile.class).snapshot();
    }

    @Test void recordsWaitUntilTickAndUnchangedTicksDoNotWrite() throws Exception {
        for (int i = 0; i < 100; i++) history.record(Fixtures.message(MessageKind.JOIN, "event " + i));
        assertEquals(50, history.size());
        assertEquals(0, history.pendingWrites());
        assertTrue(saved().entries().isEmpty());
        history.tick(); awaitWrites();
        assertEquals(50, saved().entries().size());
        assertEquals(101, saved().nextSequence());
        var marker = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(file, marker);
        history.tick(); awaitWrites();
        assertEquals(marker, Files.getLastModifiedTime(file));
    }

    @Test void clearDiscardsDirtyHistoryAndLaterRecordsWaitForNextTick() throws Exception {
        history.record(Fixtures.message(MessageKind.JOIN, "old without tick"));
        history.clear().get(5, TimeUnit.SECONDS);
        history.record(Fixtures.message(MessageKind.JOIN, "new"));
        assertTrue(saved().entries().isEmpty());
        history.tick(); awaitWrites();
        assertEquals(1, saved().entries().size());
        assertEquals(2, saved().entries().get(0).sequence());
        assertEquals("\"new\"", saved().entries().get(0).message().renderedJson());
    }

    @Test void configurationShrinkPersistsDirtyHistoryImmediately() throws Exception {
        history.record(Fixtures.message(MessageKind.JOIN, "old"));
        history.record(Fixtures.message(MessageKind.JOIN, "new"));
        history.config().set("historyLimit", "1");
        history.configChanged(); awaitWrites();
        assertEquals(1, saved().entries().size());
        assertEquals(2, saved().entries().get(0).sequence());
    }

    @Test void shutdownFlushesRecordsReceivedAfterTheLastTick() throws Exception {
        history.record(Fixtures.message(MessageKind.LEAVE, "final leave"));
        history.close(); history = null;
        assertEquals("\"final leave\"", saved().entries().get(0).message().renderedJson());
    }

    @Test void joinTickIncludesNewEventsForEveryRecipientAndClearInvalidatesReplay() throws Exception {
        var players = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players);
        var outputs = new ArrayList<List<String>>();
        var recipients = new ArrayList<ServerPlayer>();
        for (int i = 0; i < 2; i++) {
            var player = mock(ServerPlayer.class);
            var id = UUID.randomUUID();
            when(player.getUUID()).thenReturn(id);
            when(player.getChatVisibility()).thenReturn(ChatVisiblity.FULL);
            GameTestPlayers.language(player);
            when(players.getPlayer(id)).thenReturn(player);
            var output = new ArrayList<String>(); outputs.add(output); recipients.add(player);
            doAnswer(call -> { output.add(((Component) call.getArgument(0)).getString()); return null; })
                    .when(player).sendSystemMessage(any(Component.class));
            history.joined(player);
        }
        history.record(Fixtures.message(MessageKind.JOIN, "own join"));
        history.tick(); awaitWrites();
        for (var output : outputs) assertEquals(List.of("own join"), output.subList(1, output.size() - 1));
        history.clear().get(5, TimeUnit.SECONDS);
        history.record(Fixtures.message(MessageKind.JOIN, "after clear"));
        for (int i = 0; i < 2; i++) { outputs.get(i).clear(); history.joined(recipients.get(i)); }
        history.tick(); awaitWrites();
        for (var output : outputs) assertEquals(List.of("after clear"), output.subList(1, output.size() - 1));
    }
}
