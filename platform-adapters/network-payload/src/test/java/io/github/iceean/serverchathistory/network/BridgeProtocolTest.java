package io.github.iceean.serverchathistory.network;
import io.github.iceean.serverchathistory.text.ComponentFormat;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BridgeProtocolTest {
    @Test void typedEnvelopeMatchesLegacyByteArrayChannel() {
        var frame = new PortableProtocol.Hello(new UUID(1, 2), 2, ComponentFormat.MC_1_20_1);
        byte[] bytes = PortableProtocol.encode(frame);
        var typed = new FriendlyByteBuf(Unpooled.buffer()); var legacy = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GameNetworking.Bridge.CODEC.encode(typed, new GameNetworking.Bridge(bytes));
            legacy.writeByteArray(bytes);
            assertEquals(legacy, typed);
            assertEquals(frame, PortableProtocol.decode(GameNetworking.Bridge.CODEC.decode(legacy).bytes()));
            assertEquals(0, legacy.readableBytes());
            assertArrayEquals(bytes, typed.readByteArray(PortableProtocol.MAX_FRAME));
        } finally { typed.release(); legacy.release(); }
    }
    @Test void rejectsOversizeLegacyEnvelopeBeforeAllocation() {
        var input = new FriendlyByteBuf(Unpooled.buffer());
        try { input.writeVarInt(PortableProtocol.MAX_FRAME + 1); assertThrows(RuntimeException.class, () -> GameNetworking.Bridge.CODEC.decode(input)); }
        finally { input.release(); }
    }
}
