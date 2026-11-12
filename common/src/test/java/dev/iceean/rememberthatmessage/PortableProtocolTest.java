package dev.iceean.rememberthatmessage;
import dev.iceean.rememberthatmessage.core.*;
import dev.iceean.rememberthatmessage.network.*;
import dev.iceean.rememberthatmessage.text.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PortableProtocolTest {
    private final UUID token = new UUID(1,2);
    private PortableProtocol.Item item(UUID id,long sequence) {
        return new PortableProtocol.Item(id,sequence,MessageKind.CHAT,Instant.EPOCH,ComponentFormat.PORTABLE_V1,"\"你好\"",new UUID(3,4),"Alice",false);
    }
    @Test void allFramesRoundTripWithoutGameRegistries() {
        for (var format:ComponentFormat.values()) {
            for (var frame:List.of(new PortableProtocol.Hello(token,2,format),new PortableProtocol.Reply(token,2,format,ReplayPreferences.DEFAULT),
                    new PortableProtocol.Status(token,true,ReplayPreferences.DEFAULT,"enhanced"),new PortableProtocol.Boundary(token,true,10000),item(token,1)))
                assertEquals(frame,PortableProtocol.decode(PortableProtocol.encode(frame)));
        }
    }
    @Test void malformedLengthsTruncationTrailingDataAndLargeBodiesAreRejected() {
        byte[] valid=PortableProtocol.encode(item(token,1));
        for(int length=0;length<valid.length;length++) {
            byte[] shorter=Arrays.copyOf(valid,length);assertThrows(IllegalArgumentException.class,()->PortableProtocol.decode(shorter));
        }
        assertThrows(IllegalArgumentException.class,()->PortableProtocol.decode(Arrays.copyOf(valid,valid.length+1)));
        var huge=new PortableProtocol.Item(token,1,MessageKind.JOIN,Instant.EPOCH,ComponentFormat.PORTABLE_V1,"汉".repeat(100000),null,null,false);
        assertThrows(IllegalArgumentException.class,()->PortableProtocol.encode(huge));
        byte[] hello=PortableProtocol.encode(new PortableProtocol.Hello(token,2,ComponentFormat.MC_26_3));
        Arrays.fill(hello,21,25,(byte)0xff);assertThrows(IllegalArgumentException.class,()->PortableProtocol.decode(hello));
        byte[] invalidUtf=PortableProtocol.encode(new PortableProtocol.Hello(token,2,ComponentFormat.MC_26_3));
        invalidUtf[25]=(byte)0xff;assertThrows(IllegalArgumentException.class,()->PortableProtocol.decode(invalidUtf));
    }
    @Test void negotiationTimesOutRejectsLateReplyAndWrongSession() {
        var state=new PlaySession(0);assertTrue(state.offer());assertFalse(state.offer());
        state.reply(new PortableProtocol.Reply(token,2,ComponentFormat.MC_1_20_1,ReplayPreferences.DEFAULT));assertFalse(state.accepted());
        assertFalse(state.ready(1_999_999_999L));assertTrue(state.ready(2_000_000_000L));
        state.reply(new PortableProtocol.Reply(state.id(),2,ComponentFormat.MC_1_20_1,ReplayPreferences.DEFAULT));assertFalse(state.accepted());
        assertEquals("timeout",state.reason());
    }
    @Test void successfulReplyFreezesPreferencesAndVersionMismatchFallsBack() {
        var state=new PlaySession(0);state.offer();var preferences=new ReplayPreferences(false,10,3);
        state.reply(new PortableProtocol.Reply(state.id(),2,ComponentFormat.MC_1_20_1,preferences));
        assertTrue(state.accepted());assertTrue(state.ready(1));assertEquals(preferences,state.preferences());
        state.reply(new PortableProtocol.Reply(state.id(),2,ComponentFormat.MC_26_3,ReplayPreferences.DEFAULT));assertEquals(preferences,state.preferences());
        state=new PlaySession(0);state.offer();state.reply(new PortableProtocol.Reply(state.id(),99,ComponentFormat.MC_26_3,preferences));
        assertFalse(state.accepted());assertEquals("version_mismatch",state.reason());
    }
    @Test void batchRejectsDuplicatesLateOldBackendAndUnnegotiatedItems() {
        var client=new ClientPlaySession();assertFalse(client.accept(item(token,1)));
        assertTrue(client.hello(new PortableProtocol.Hello(token,2,ComponentFormat.MC_1_20_1)));
        assertFalse(client.hello(new PortableProtocol.Hello(token,2,ComponentFormat.MC_26_3)));
        assertFalse(client.accept(item(token,1)));
        assertTrue(client.accept(new PortableProtocol.Status(token,true,ReplayPreferences.DEFAULT,"enhanced")));
        assertTrue(client.accept(new PortableProtocol.Boundary(token,true,2)));
        assertTrue(client.accept(item(token,1)));assertFalse(client.accept(item(token,1)));assertTrue(client.accept(item(token,2)));assertFalse(client.accept(item(token,3)));
        assertTrue(client.accept(new PortableProtocol.Boundary(token,false,2)));assertFalse(client.accept(new PortableProtocol.Boundary(token,true,2)));
        var backend=new UUID(5,6);client.reset();
        assertFalse(client.hello(new PortableProtocol.Hello(token,2,ComponentFormat.MC_1_20_1)));
        client.hello(new PortableProtocol.Hello(backend,2,ComponentFormat.MC_26_3));
        assertFalse(client.accept(item(token,3)));assertFalse(client.accept(new PortableProtocol.Status(token,true,ReplayPreferences.DEFAULT,"enhanced")));
        client.reset();assertFalse(client.active());assertFalse(client.accept(item(backend,1)));
    }
}
