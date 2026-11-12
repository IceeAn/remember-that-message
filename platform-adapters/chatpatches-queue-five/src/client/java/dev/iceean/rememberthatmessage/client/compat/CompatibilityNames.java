package dev.iceean.rememberthatmessage.client.compat;

import java.util.Map;
import org.objectweb.asm.tree.*;

/** Normalizes only the 1.20.1 symbols used by our optional bytecode contracts.
 * Published mods use intermediary names; Loom development mods use Mojang names.
 * Reading either form must not load Minecraft classes during Mixin selection. */
final class CompatibilityNames {
    private static final Map<String, String> TYPES = Map.of(
            "net/minecraft/class_2561", "net/minecraft/network/chat/Component",
            "net/minecraft/class_5250", "net/minecraft/network/chat/MutableComponent",
            "net/minecraft/class_338", "net/minecraft/client/gui/components/ChatComponent",
            "net/minecraft/class_7469", "net/minecraft/network/chat/MessageSignature",
            "net/minecraft/class_7591", "net/minecraft/client/GuiMessageTag",
            "net/minecraft/class_640", "net/minecraft/client/multiplayer/PlayerInfo",
            "net/minecraft/class_2556$class_7602", "net/minecraft/network/chat/ChatType$Bound");
    private CompatibilityNames() { }
    private static String descriptor(String value) {
        for (var type : TYPES.entrySet()) value = value.replace("L" + type.getKey() + ";", "L" + type.getValue() + ";");
        return value;
    }
    static void normalize(ClassNode node) {
        for (var field : node.fields) field.desc = descriptor(field.desc);
        for (var method : node.methods) {
            method.desc = descriptor(method.desc);
            for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/class_338") && (call.name.equals("method_44811") || call.name.equals("method_1815"))) call.name = "addMessage";
                call.owner = TYPES.getOrDefault(call.owner, call.owner);
                call.desc = descriptor(call.desc);
            }
        }
    }
}
