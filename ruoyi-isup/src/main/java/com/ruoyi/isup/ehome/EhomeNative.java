package com.ruoyi.isup.ehome;

import java.nio.charset.StandardCharsets;

/** Fixed native buffers are not Java strings; some SDK versions report a binary version. */
public final class EhomeNative {
    private EhomeNative() { }
    public static String text(byte[] bytes) {
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8).trim();
    }
    public static String version(byte[] bytes) {
        if (bytes.length == 0 || bytes[0] == 0) return "";
        int first = Byte.toUnsignedInt(bytes[0]);
        if (first < 10) return first + "." + (bytes.length > 1 ? Byte.toUnsignedInt(bytes[1]) : 0);
        return text(bytes);
    }
    public static void copy(String value, byte[] target) {
        byte[] source = value.getBytes(StandardCharsets.UTF_8);
        if (source.length >= target.length) throw new IllegalArgumentException("SDK 地址超出长度限制");
        System.arraycopy(source, 0, target, 0, source.length);
    }
    public static boolean legacy(String version) {
        return version != null && version.matches("[234](\\.[0-9]+)*");
    }
    public static boolean requiresPush(String version) {
        if (!legacy(version)) throw new IllegalArgumentException("设备协议版本未识别为 EHome 2.x / 3.x / 4.x");
        return version.startsWith("4");
    }
}
