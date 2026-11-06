package io.github.iceean.serverchathistory.client.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contract probes do not load Chat Patches or start a Minecraft client. */
class ChatPatchesShapeTest {
    private static ClassNode[] shape() {
        var config = new ClassNode(); var util = new ClassNode(); var data = new ClassNode(); var log = new ClassNode();
        config.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "makeTimestamp", "(Ljava/util/Date;ZZ)Lnet/minecraft/network/chat/MutableComponent;", null, null));
        data.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Lcom/mojang/authlib/GameProfile;Ljava/util/Date;Z)V", null, null));
        util.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "messageData", "Lobro1961/chatpatches/util/ChatUtil$MessageData;", null, null));
        var modify = new MethodNode(); modify.name = "modifyMessage";
        modify.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "obro1961/chatpatches/util/ChatUtil$MessageData", "timestamp", "Ljava/util/Date;"));
        util.methods.add(modify);
        var queue = new MethodNode(); queue.name = "queueMessage";
        queue.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "obro1961/chatpatches/util/ChatUtil", "messageData", "Lobro1961/chatpatches/util/ChatUtil$MessageData;"));
        log.methods.add(queue);
        return new ClassNode[] {config, util, data, log};
    }
    private static boolean compatible(ClassNode[] nodes) {
        return ChatPatchesMixinPlugin.compatible(nodes[0], nodes[1], nodes[2], nodes[3]);
    }
    @Test void requiresBothTimestampConsumptionAndQueuedMetadataRetention() {
        var nodes = shape(); assertTrue(compatible(nodes));
        nodes[3].methods.clear(); assertFalse(compatible(nodes));
        nodes = shape(); nodes[1].methods.clear(); assertFalse(compatible(nodes));
    }
    @Test void rejectsChangedDescriptorsAndInaccessibleOrImmutableMetadata() {
        var nodes = shape(); nodes[0].methods.get(0).desc = "(Ljava/util/Date;)V"; assertFalse(compatible(nodes));
        nodes = shape(); nodes[0].methods.get(0).access |= Opcodes.ACC_STATIC; assertFalse(compatible(nodes));
        nodes = shape(); nodes[1].fields.get(0).access |= Opcodes.ACC_FINAL; assertFalse(compatible(nodes));
        nodes = shape(); nodes[2].methods.get(0).access = Opcodes.ACC_PRIVATE; assertFalse(compatible(nodes));
    }
    @Test void mixinStartsDisabledAndTaggedDatePreservesTimeAndEventKind() {
        assertFalse(new ChatPatchesMixinPlugin().shouldApplyMixin("unused", "unused"));
        var date = new HistoricalDate(123456789L, true);
        assertEquals(123456789L, date.getTime()); assertTrue(date.system());
        assertFalse(new HistoricalDate(123456789L, false).system());
    }
}
