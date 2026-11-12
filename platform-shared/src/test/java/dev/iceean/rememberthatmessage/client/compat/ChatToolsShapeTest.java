package dev.iceean.rememberthatmessage.client.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatToolsShapeTest {
    private static ClassNode[] shape() {
        var timestamp = new ClassNode(); var unit = new ClassNode();
        int flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        var work = new MethodNode(flags, "work", ChatToolsMixinPlugin.WORK, null, null);
        work.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/time/Instant", "now", "()Ljava/time/Instant;"));
        work.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ChatToolsMixinPlugin.TIMESTAMP, "timeInFormat", "(Ljava/lang/String;)Ljava/lang/String;"));
        timestamp.methods.add(work);
        timestamp.methods.add(new MethodNode(flags, "timeInFormat", "(Ljava/lang/String;)Ljava/lang/String;", null, null));
        timestamp.methods.add(new MethodNode(flags, "timeInFormat", "(Ljava/lang/String;Ljava/time/LocalDateTime;)Ljava/lang/String;", null, null));
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", ChatToolsMixinPlugin.CONSTRUCTOR, null, null);
        ctor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, ChatToolsMixinPlugin.UNIT, "unixTimestamp", "J"));
        unit.methods.add(ctor); unit.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "unixTimestamp", "J", null, null));
        return new ClassNode[]{timestamp, unit};
    }
    @Test void checksBothClockSitesAndCopyMetadata() {
        var nodes = shape(); assertTrue(ChatToolsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes[0].methods.get(0).instructions.remove(nodes[0].methods.get(0).instructions.getFirst());
        assertFalse(ChatToolsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes = shape(); nodes[0].methods.remove(nodes[0].methods.size() - 1);
        assertFalse(ChatToolsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes = shape(); nodes[1].methods.get(0).instructions.clear();
        assertFalse(ChatToolsMixinPlugin.compatible(nodes[0], nodes[1]));
    }
    @Test void rejectsAmbiguousClocksAndChangedConstructorAndStartsDisabled() {
        var nodes = shape();
        nodes[0].methods.get(0).instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/time/Instant", "now", "()Ljava/time/Instant;"));
        assertFalse(ChatToolsMixinPlugin.compatible(nodes[0], nodes[1]));
        nodes = shape(); nodes[1].methods.get(0).desc = "()V";
        assertFalse(ChatToolsMixinPlugin.compatible(nodes[0], nodes[1]));
        assertFalse(new ChatToolsMixinPlugin().shouldApplyMixin("unused", "unused"));
    }
}
