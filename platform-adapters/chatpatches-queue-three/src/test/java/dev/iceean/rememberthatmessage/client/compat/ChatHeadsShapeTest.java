package dev.iceean.rememberthatmessage.client.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatHeadsShapeTest {
    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
    private static ClassNode[] heads() {
        var heads = new ClassNode(); var data = new ClassNode();
        String type = "Ldzwdz/chat_heads/HeadData;";
        data.fields.add(new FieldNode(PUBLIC_STATIC | Opcodes.ACC_FINAL, "EMPTY", type, null, null));
        for (String name : new String[]{"lastSenderData", "lineData", "refreshingLineData"})
            heads.fields.add(new FieldNode(PUBLIC_STATIC, name, type, null, null));
        for (String name : new String[]{"refreshing", "serverSentUuid"})
            heads.fields.add(new FieldNode(PUBLIC_STATIC, name, "Z", null, null));
        heads.methods.add(new MethodNode(PUBLIC_STATIC, "handleAddedMessage", GameChatHeads.DESCRIPTOR, null, null));
        return new ClassNode[]{heads, data};
    }
    @Test void rejectsChangedOrImmutableHeadMetadata() {
        var nodes = heads(); assertTrue(ChatHeadsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes[0].fields.getFirst().access |= Opcodes.ACC_FINAL;
        assertFalse(ChatHeadsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes = heads(); nodes[0].methods.getFirst().desc = "()V";
        assertFalse(ChatHeadsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes = heads(); nodes[0].fields.removeLast();
        assertFalse(ChatHeadsMixinPlugin.compatible(nodes[0], nodes[1]));
    }
    @Test void requiresRecognizableQueueAndExactlyOneDrainInsertion() {
        var node = new ClassNode();
        node.methods.add(new MethodNode(PUBLIC_STATIC, "queueMessage", ChatHeadsMixinPlugin.QUEUE_DESC, null, null));
        var drain = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "drainPendingMessages", "()V", null, null);
        node.methods.add(drain);
        assertFalse(ChatHeadsMixinPlugin.queueCompatible(node));
        drain.instructions.add(insert()); assertTrue(ChatHeadsMixinPlugin.queueCompatible(node));
        drain.instructions.add(insert()); assertFalse(ChatHeadsMixinPlugin.queueCompatible(node));
        assertFalse(new ChatHeadsMixinPlugin().shouldApplyMixin("unused", "unused"));
    }
    private static MethodInsnNode insert() {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/components/ChatComponent", "addMessage", ChatHeadsMixinPlugin.INSERT_DESC);
    }
}
