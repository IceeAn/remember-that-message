package io.github.iceean.serverchathistory;

import io.github.iceean.serverchathistory.config.*;
import io.github.iceean.serverchathistory.core.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryPolicyTest {
    @Test void unchangedSnapshotsAreSharedAndPreviouslyPublishedSnapshotsStayImmutable() {
        var buffer = new HistoryBuffer(HistorySnapshot.empty(), 2);
        var empty = buffer.snapshot();
        assertSame(empty, buffer.snapshot());
        buffer.append(Fixtures.message(MessageKind.JOIN, "one"));
        var one = buffer.snapshot();
        assertSame(one, buffer.snapshot());
        buffer.resize(10);
        assertSame(one, buffer.snapshot());
        buffer.append(Fixtures.message(MessageKind.JOIN, "two"));
        var two = buffer.snapshot();
        buffer.resize(1);
        var trimmed = buffer.snapshot();
        buffer.clear();
        assertTrue(empty.entries().isEmpty());
        assertEquals(1, one.entries().size());
        assertEquals(2, two.entries().size());
        assertEquals(2, trimmed.entries().getFirst().sequence());
        assertTrue(buffer.snapshot().entries().isEmpty());
        assertEquals(3, buffer.snapshot().nextSequence());
        assertThrows(UnsupportedOperationException.class, () -> two.entries().clear());
    }

    @Test void keepsLatestRecordsAndSequenceAcrossRestartAndClockChanges() {
        var buffer = new HistoryBuffer(HistorySnapshot.empty(), 2);
        buffer.append(Fixtures.message(MessageKind.JOIN, "first"));
        buffer.append(Fixtures.message(MessageKind.LEAVE, "second"));
        buffer.append(new HistoryEntry.Message(MessageKind.JOIN, Instant.EPOCH, "\"backwards clock\"", null));
        assertEquals(List.of(2L, 3L), buffer.snapshot().entries().stream().map(HistoryEntry::sequence).toList());
        var restored = new HistoryBuffer(buffer.snapshot(), 2);
        restored.append(Fixtures.message(MessageKind.JOIN, "new login"));
        assertEquals(List.of(3L, 4L), restored.snapshot().entries().stream().map(HistoryEntry::sequence).toList());
    }

    @Test void eventSwitchHidesWithoutDeletingAndOwnJoinIsIncluded() {
        var buffer = new HistoryBuffer(HistorySnapshot.empty(), 50);
        buffer.append(Fixtures.message(MessageKind.DEATH, "death"));
        buffer.append(Fixtures.message(MessageKind.JOIN, "current join"));
        var hidden = ConfigCodec.with(ModConfig.defaults(), "events.join", "false");
        assertEquals(1, ReplayPolicy.RECENT.select(buffer.snapshot(), hidden).size());
        assertEquals(2, buffer.size());
        assertEquals(2, ReplayPolicy.RECENT.select(buffer.snapshot(), ModConfig.defaults()).size());
    }

    @Test void resizeEvictsImmediatelyAndGrowingCannotRecover() {
        var buffer = new HistoryBuffer(Fixtures.snapshot("old"), Long.MAX_VALUE);
        buffer.append(Fixtures.message(MessageKind.JOIN, "new"));
        buffer.resize(1);
        buffer.resize(100);
        assertEquals(1, buffer.size());
        assertEquals(2, buffer.snapshot().entries().getFirst().sequence());
    }

    @Test void clearPreservesSequenceAndWorldsAreIndependent() {
        var first = new HistoryBuffer(Fixtures.snapshot("world one"), 50);
        var second = new HistoryBuffer(HistorySnapshot.empty(), 50);
        first.clear();
        first.append(Fixtures.message(MessageKind.JOIN, "after clear"));
        assertEquals(2, first.snapshot().entries().getFirst().sequence());
        assertEquals(0, second.size());
    }

    @Test void errorNoticeIsIndependentFromEmptyNotice() {
        var config = ConfigCodec.with(ModConfig.defaults(), "showEmptyHistory", "false");
        assertEquals(List.of(NoticePolicy.Notice.CONFIG_ERROR), NoticePolicy.after(0, config, true));
        assertEquals(List.of(NoticePolicy.Notice.END, NoticePolicy.Notice.CONFIG_ERROR), NoticePolicy.after(2, config, true));
        config = ConfigCodec.with(config, "showConfigErrors", "false");
        assertTrue(NoticePolicy.after(0, config, true).isEmpty());
    }

    @Test void autoNeverUsesUnsignedPacketsWhenSecurityIsEnforced() {
        assertTrue(ReplayPolicy.usePlayerPackets(ModConfig.defaults(), false));
        assertFalse(ReplayPolicy.usePlayerPackets(ModConfig.defaults(), true));
        assertFalse(ReplayPolicy.usePlayerPackets(ConfigCodec.with(ModConfig.defaults(), "replayMode", "system"), false));
    }

    @Test void onlyConsoleOperatorsAndIntegratedOwnerCanAdminister() {
        assertTrue(AdminPolicy.allows(true, false, false, false));
        assertTrue(AdminPolicy.allows(false, true, true, false));
        assertTrue(AdminPolicy.allows(false, true, false, true));
        assertFalse(AdminPolicy.allows(false, true, false, false));
        assertFalse(AdminPolicy.allows(false, false, true, true));
    }

    @Test void rejectsUnsupportedOrUnorderedSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> new HistorySnapshot(2, 1, List.of()));
        var entry = Fixtures.snapshot("one").entries().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new HistorySnapshot(1, 3, List.of(entry, entry)));
        assertThrows(IllegalArgumentException.class, () -> new HistorySnapshot(1, 1, List.of(entry)));
    }
}
