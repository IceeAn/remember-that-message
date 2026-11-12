package dev.iceean.rememberthatmessage.client.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;

public final class ChatToolsMixinPlugin implements IMixinConfigPlugin {
    static final String TIMESTAMP = "net/apple70cents/chattools/features/general/Timestamp";
    static final String UNIT = "net/apple70cents/chattools/utils/TextUtils$MessageUnit";
    static final String WORK = "(Lnet/minecraft/network/chat/Component;Ljava/lang/String;)Lnet/minecraft/network/chat/Component;";
    static final String CONSTRUCTOR = "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;JIZ)V";
    private boolean candidate;
    private boolean patches;
    public void onLoad(String mixinPackage) {
        if (!IntegrationBootstrap.ENABLED) return;
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("chattools")) { ChatToolsBootstrap.reason = "absent"; return; }
        try {
            boolean valid = compatible(node("chattools", TIMESTAMP), node("chattools", UNIT));
            patches = loader.isModLoaded("chatpatches");
            if (patches) valid &= ChatPatchesQueueShape.compatible(node("chatpatches", "obro1961/chatpatches/ChatLog"));
            candidate = valid;
            ChatToolsBootstrap.compatible = valid;
            ChatToolsBootstrap.needsQueueHooks = patches;
            ChatToolsBootstrap.reason = valid ? "ready" : "incompatible";
        } catch (Exception | LinkageError error) { ChatToolsBootstrap.reason = "incompatible"; }
    }
    static boolean compatible(ClassNode timestamp, ClassNode unit) {
        var work = method(timestamp, "work", WORK, true);
        var ctor = method(unit, "<init>", CONSTRUCTOR, false);
        return unit.methods.stream().filter(m -> m.name.equals("<init>")).count() == 1
                && work != null && ctor != null
                && method(timestamp, "timeInFormat", "(Ljava/lang/String;)Ljava/lang/String;", true) != null
                && method(timestamp, "timeInFormat", "(Ljava/lang/String;Ljava/time/LocalDateTime;)Ljava/lang/String;", true) != null
                && calls(work, "java/time/Instant", "now", "()Ljava/time/Instant;") == 1
                && calls(work, TIMESTAMP, "timeInFormat", "(Ljava/lang/String;)Ljava/lang/String;") == 1
                && unit.fields.stream().anyMatch(f -> f.name.equals("unixTimestamp") && f.desc.equals("J")
                    && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC)
                && writesTimestamp(ctor);
    }
    private static boolean writesTimestamp(MethodNode method) {
        for (var insn : method.instructions) if (insn instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.PUTFIELD && field.owner.equals(UNIT)
                && field.name.equals("unixTimestamp") && field.desc.equals("J")) return true;
        return false;
    }
    private static MethodNode method(ClassNode node, String name, String desc, boolean isStatic) {
        int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT;
        int expected = Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0);
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)
                && (m.access & mask) == expected).findFirst().orElse(null);
    }
    private static int calls(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) count++;
        return count;
    }
    private static ClassNode node(String mod, String name) throws Exception {
        var path = FabricLoader.getInstance().getModContainer(mod).orElseThrow().findPath(name + ".class").orElseThrow();
        var result = new ClassNode();
        new ClassReader(java.nio.file.Files.readAllBytes(path)).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        CompatibilityNames.normalize(result);
        return result;
    }
    public boolean shouldApplyMixin(String target, String mixin) {
        return candidate && (!mixin.endsWith("ChatPatchesToolsQueueMixin") || patches);
    }
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> own, Set<String> other) { }
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("ChatToolsTimestampMixin")) ChatToolsBootstrap.timestampHooks = true;
        else if (mixin.endsWith("ChatToolsMessageTimeMixin")) ChatToolsBootstrap.messageHooks = true;
        else if (mixin.endsWith("ChatPatchesToolsQueueMixin")) ChatToolsBootstrap.queueHooks = true;
    }
}
