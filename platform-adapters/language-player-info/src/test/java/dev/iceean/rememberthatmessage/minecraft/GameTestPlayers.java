package dev.iceean.rememberthatmessage.minecraft;

import net.minecraft.server.level.ServerPlayer;

/** 为各代玩家语言 API 补齐测试替身，避免复制完整业务回归。 */
public final class GameTestPlayers {
    private GameTestPlayers() { }
    public static void language(ServerPlayer player) { org.mockito.Mockito.when(player.clientInformation()).thenReturn(net.minecraft.server.level.ClientInformation.createDefault()); }
}
