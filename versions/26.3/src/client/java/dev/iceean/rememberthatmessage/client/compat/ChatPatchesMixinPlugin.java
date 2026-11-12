package dev.iceean.rememberthatmessage.client.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;

/** Optional, early shape checks keep unknown or disabled integrations out of transformation. */
public final class ChatPatchesMixinPlugin implements IMixinConfigPlugin {
    private boolean candidate;
    public void onLoad(String mixinPackage) {
        if (!IntegrationBootstrap.ENABLED) return;
        if (!FabricLoader.getInstance().isModLoaded("chatpatches")) { ChatPatchesBootstrap.reason = "absent"; return; }
        try {
            var config = node("config/Config");
            var util = node("util/ChatUtil");
            var data = node("util/ChatUtil$MessageData");
            var log = node("ChatLog");
            boolean valid = compatible(config, util, data, log);
            candidate = valid;
            ChatPatchesBootstrap.reason = valid ? "checking" : "incompatible";
        } catch (Exception failure) { ChatPatchesBootstrap.reason = "incompatible"; }
    }
    static boolean compatible(ClassNode config, ClassNode util, ClassNode data, ClassNode log) {
        return method(config, "makeTimestamp", "(Ljava/util/Date;ZZ)Lnet/minecraft/network/chat/MutableComponent;")
                && method(data, "<init>", "(Lcom/mojang/authlib/GameProfile;Ljava/util/Date;Z)V")
                && util.fields.stream().anyMatch(f -> f.name.equals("messageData")
                        && f.desc.equals("Lobro1961/chatpatches/util/ChatUtil$MessageData;")
                        && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                && accesses(util, "modifyMessage", "obro1961/chatpatches/util/ChatUtil$MessageData", "timestamp")
                && accesses(log, "queueMessage", "obro1961/chatpatches/util/ChatUtil", "messageData");
    }
    private static ClassNode node(String suffix) throws Exception {
        var path = FabricLoader.getInstance().getModContainer("chatpatches").orElseThrow()
                .findPath("obro1961/chatpatches/" + suffix + ".class").orElseThrow();
        var result = new ClassNode();
        new ClassReader(java.nio.file.Files.readAllBytes(path)).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }
    private static boolean method(ClassNode node, String name, String desc) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc)
                && (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == Opcodes.ACC_PUBLIC);
    }
    private static boolean accesses(ClassNode node, String method, String owner, String name) {
        for (var m : node.methods) if (m.name.equals(method))
            for (var insn : m.instructions) if (insn instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(name)) return true;
        return false;
    }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return candidate; }
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> own, Set<String> other) { }
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        ChatPatchesBootstrap.hooks = true; ChatPatchesBootstrap.reason = "ready";
    }
}
