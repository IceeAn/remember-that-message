package io.github.iceean.serverchathistory.client.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityNamesTest {
    @Test void publishedQueueContractMatchesDevelopmentNamesWithoutLoadingGameClasses() {
        var node = new ClassNode();
        String args = "(Lnet/minecraft/class_2561;Lnet/minecraft/class_7469;Lnet/minecraft/class_7591;)";
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "queueMessage", args + "Z", null, null));
        var drain = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "drainPendingMessages", "()V", null, null);
        drain.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_338", "method_44811", args + "V"));
        node.methods.add(drain);
        assertFalse(ChatPatchesQueueShape.compatible(node));
        CompatibilityNames.normalize(node);
        assertTrue(ChatPatchesQueueShape.compatible(node));
        CompatibilityNames.normalize(node);
        assertTrue(ChatPatchesQueueShape.compatible(node));
        drain.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/components/ChatComponent", "addMessage", ChatPatchesQueueShape.INSERT_DESC));
        assertFalse(ChatPatchesQueueShape.compatible(node));
    }
}
