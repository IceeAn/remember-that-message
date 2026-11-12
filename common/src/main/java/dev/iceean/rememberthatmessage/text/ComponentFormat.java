package dev.iceean.rememberthatmessage.text;

/** 原生编码按正式版本区分；相同 JSON 形状不保证注册表内容可互读。 */
public enum ComponentFormat {
    MC_1_20("1.20", false),
    MC_1_20_1("1.20.1", false),
    MC_1_20_2("1.20.2", false),
    MC_1_20_3("1.20.3", false),
    MC_1_20_4("1.20.4", false),
    MC_1_20_5("1.20.5", false),
    MC_1_20_6("1.20.6", false),
    MC_1_21("1.21", false),
    MC_1_21_1("1.21.1", false),
    MC_1_21_2("1.21.2", false),
    MC_1_21_3("1.21.3", false),
    MC_1_21_4("1.21.4", false),
    MC_1_21_5("1.21.5", true),
    MC_1_21_6("1.21.6", true),
    MC_1_21_7("1.21.7", true),
    MC_1_21_8("1.21.8", true),
    MC_1_21_9("1.21.9", true),
    MC_1_21_10("1.21.10", true),
    MC_1_21_11("1.21.11", true),
    MC_26_3("26.3", true),
    PORTABLE_V1(null, false);

    private final String minecraftVersion;
    private final boolean modern;
    ComponentFormat(String minecraftVersion, boolean modern) {
        this.minecraftVersion = minecraftVersion; this.modern = modern;
    }
    public boolean modern() { return modern; }
    public static ComponentFormat forMinecraftVersion(String version) {
        for (var format : values()) if (version.equals(format.minecraftVersion)) return format;
        throw new IllegalArgumentException("Unsupported Minecraft text encoding: " + version);
    }
}
