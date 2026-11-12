package dev.iceean.rememberthatmessage;

import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.network.*;
import dev.iceean.rememberthatmessage.text.ComponentFormat;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientBatchIntegrityTest {
    private final UUID token = UUID.randomUUID();
    private ClientPlaySession start(int count) {
        var client = new ClientPlaySession();
        assertTrue(client.hello(new PortableProtocol.Hello(token, 2, ComponentFormat.MC_26_3)));
        assertTrue(client.accept(new PortableProtocol.Status(token, true, ReplayPreferences.DEFAULT, "enhanced")));
        assertTrue(client.accept(new PortableProtocol.Boundary(token, true, count)));
        return client;
    }
    private PortableProtocol.Item item(long sequence) {
        return new PortableProtocol.Item(token, sequence, MessageKind.JOIN, Instant.EPOCH, ComponentFormat.PORTABLE_V1,
                "\"text\"", null, null, false);
    }
    @Test void truncatedBatchClosesBothLayersAndReportsOnce() {
        var client = start(3); client.accept(item(1));
        assertNull(client.takeIncomplete());
        assertTrue(client.accept(new PortableProtocol.Boundary(token, false, 3)));
        assertEquals(new ClientPlaySession.Incomplete(3, 1, 0, 3), client.takeIncomplete());
        assertNull(client.takeIncomplete());
        assertFalse(client.accept(item(2)));
        assertFalse(client.accept(new PortableProtocol.Boundary(token, false, 3)));
        assertFalse(client.accept(new PortableProtocol.Boundary(token, true, 3)));
    }
    @Test void wrongEndCountFailsEvenWhenAllItemsArrive() {
        for (int end : new int[] {0, 2, 999}) {
            var client = start(3);
            for (int i = 1; i <= 3; i++) assertTrue(client.accept(item(i)));
            assertTrue(client.accept(new PortableProtocol.Boundary(token, false, end)));
            assertEquals(new ClientPlaySession.Incomplete(3, 3, 0, end), client.takeIncomplete());
        }
    }
    @Test void completeAndEmptyBatchesNeedNoWarning() {
        for (int count : new int[] {0, 3}) {
            var client = start(count);
            for (int i = 1; i <= count; i++) assertTrue(client.accept(item(i)));
            client.accept(new PortableProtocol.Boundary(token, false, count));
            assertNull(client.takeIncomplete());
        }
    }
    @Test void localDecodeFailureOrPlaceholderIsReportedWithoutFalsifyingReceivedCount() {
        var client = start(3);
        for (int i = 1; i <= 3; i++) {
            assertTrue(client.accept(item(i)));
            if (i == 2) client.unavailable();
        }
        assertNull(client.takeIncomplete());
        client.accept(new PortableProtocol.Boundary(token, false, 3));
        assertEquals(new ClientPlaySession.Incomplete(3, 3, 1, 3), client.takeIncomplete());
        client.unavailable(); assertNull(client.takeIncomplete());
    }
    @Test void duplicateOrOldBackendFramesDoNotCountAsReceived() {
        var client = start(2);
        assertTrue(client.accept(item(10))); assertFalse(client.accept(item(10)));
        var foreign = new PortableProtocol.Item(UUID.randomUUID(), 11, MessageKind.JOIN, Instant.EPOCH,
                ComponentFormat.PORTABLE_V1, "\"foreign\"", null, null, false);
        assertFalse(client.accept(foreign));
        assertFalse(client.accept(new PortableProtocol.Boundary(foreign.session(), false, 2)));
        client.accept(new PortableProtocol.Boundary(token, false, 2));
        assertEquals(new ClientPlaySession.Incomplete(2, 1, 0, 2), client.takeIncomplete());
    }
    @Test void resetDropsOldPendingWarningAndFailuresDoNotCarryAcrossConnections() {
        var client = start(1); client.unavailable();
        client.accept(new PortableProtocol.Boundary(token, false, 1));
        client.reset(); assertNull(client.takeIncomplete());
        var next = UUID.randomUUID();
        assertTrue(client.hello(new PortableProtocol.Hello(next, 2, ComponentFormat.MC_26_3)));
        client.accept(new PortableProtocol.Status(next, true, ReplayPreferences.DEFAULT, "enhanced"));
        client.accept(new PortableProtocol.Boundary(next, true, 0));
        client.accept(new PortableProtocol.Boundary(next, false, 0));
        assertNull(client.takeIncomplete());
    }
}
