package dev.iceean.rememberthatmessage.core;

public final class AdminPolicy {
    private AdminPolicy() { }
    public static boolean allows(boolean console, boolean player, boolean operatorLevelTwo, boolean worldOwner) {
        return console || (player && (operatorLevelTwo || worldOwner));
    }
}
