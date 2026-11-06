package io.github.iceean.serverchathistory.text;

/** 文件及握手使用显式编码标识，不根据版本号字符串大小猜测兼容性。 */
public enum ComponentFormat {
    MC_1_20_1, MC_1_21_1, MC_26_3, PORTABLE_V1;

    public boolean modern() { return this == MC_26_3; }
}
