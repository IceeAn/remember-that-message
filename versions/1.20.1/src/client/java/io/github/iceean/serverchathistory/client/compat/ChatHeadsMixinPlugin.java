package io.github.iceean.serverchathistory.client.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;

/** Validates optional interfaces without linking or initializing third-party classes. */
public final class ChatHeadsMixinPlugin implements IMixinConfigPlugin {
    static final String QUEUE_DESC = ChatPatchesQueueShape.QUEUE_DESC;
    static final String INSERT_DESC = ChatPatchesQueueShape.INSERT_DESC;
    private boolean candidate;
    public void onLoad(String mixinPackage) {
        if (!ChatHeadsBootstrap.ENABLED) return;
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("chat_heads")) { ChatHeadsBootstrap.reason = "absent"; return; }
        try {
            boolean compatible = compatible(node("chat_heads", "dzwdz/chat_heads/ChatHeads"), node("chat_heads", "dzwdz/chat_heads/HeadData"));
            boolean patches = loader.isModLoaded("chatpatches");
            ChatHeadsBootstrap.needsQueueHooks = patches;
            if (patches) compatible &= queueCompatible(node("chatpatches", "obro1961/chatpatches/ChatLog"));
            ChatHeadsBootstrap.compatible = compatible;
            candidate = compatible && patches;
            ChatHeadsBootstrap.reason = compatible ? "ready" : "incompatible";
        } catch (Exception | LinkageError error) { ChatHeadsBootstrap.reason = "incompatible"; }
    }
    static boolean compatible(ClassNode heads, ClassNode data) {
        String headType = "Ldzwdz/chat_heads/HeadData;";
        return method(heads, "handleAddedMessage", "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/client/multiplayer/PlayerInfo;)V", true)
                && field(data, "EMPTY", headType, false)
                && field(heads, "lastSenderData", headType, true) && field(heads, "lineData", headType, true)
                && field(heads, "refreshingLineData", headType, true)
                && field(heads, "refreshing", "Z", true) && field(heads, "serverSentUuid", "Z", true);
    }
    static boolean queueCompatible(ClassNode log) { return ChatPatchesQueueShape.compatible(log); }
    private static boolean method(ClassNode node, String name, String desc, boolean requirePublic) {
        int mask = Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | (requirePublic ? Opcodes.ACC_PUBLIC : 0);
        int expected = Opcodes.ACC_STATIC | (requirePublic ? Opcodes.ACC_PUBLIC : 0);
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc) && (m.access & mask) == expected);
    }
    private static boolean field(ClassNode node, String name, String desc, boolean mutable) {
        int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | (mutable ? Opcodes.ACC_FINAL : 0);
        return node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc)
                && (f.access & mask) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
    }
    private static ClassNode node(String mod, String name) throws Exception {
        var path = FabricLoader.getInstance().getModContainer(mod).orElseThrow().findPath(name + ".class").orElseThrow();
        var result = new ClassNode();
        new ClassReader(java.nio.file.Files.readAllBytes(path)).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        CompatibilityNames.normalize(result);
        return result;
    }
    public boolean shouldApplyMixin(String target, String mixin) { return candidate; }
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> own, Set<String> other) { }
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) { ChatHeadsBootstrap.queueHooks = true; }
}
