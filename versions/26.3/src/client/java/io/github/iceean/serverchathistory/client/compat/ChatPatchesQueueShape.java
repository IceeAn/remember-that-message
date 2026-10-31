package io.github.iceean.serverchathistory.client.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Shared structural check for optional integrations that follow Chat Patches' loading queue. */
final class ChatPatchesQueueShape {
    static final String QUEUE_DESC = "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)Z";
    static final String INSERT_DESC = QUEUE_DESC.substring(0, QUEUE_DESC.length() - 1) + "V";
    static boolean compatible(ClassNode log) {
        if (!method(log, "queueMessage", QUEUE_DESC, true) || !method(log, "drainPendingMessages", "()V", false)) return false;
        int calls = 0;
        for (var m : log.methods) if (m.name.equals("drainPendingMessages"))
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/gui/components/ChatComponent")
                    && call.name.equals("addMessage") && call.desc.equals(INSERT_DESC)) calls++;
        return calls == 1;
    }
    private static boolean method(ClassNode node, String name, String desc, boolean requirePublic) {
        int mask = Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | (requirePublic ? Opcodes.ACC_PUBLIC : 0);
        int expected = Opcodes.ACC_STATIC | (requirePublic ? Opcodes.ACC_PUBLIC : 0);
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc) && (m.access & mask) == expected);
    }
    private ChatPatchesQueueShape() { }
}
