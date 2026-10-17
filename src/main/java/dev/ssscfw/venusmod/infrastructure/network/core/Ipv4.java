package dev.ssscfw.venusmod.infrastructure.network.core;

/** 実DNSやソケットを一切呼ばない、符号なしIPv4演算。 */
public final class Ipv4 {
    private Ipv4() {}
    public static long parse(String value) {
        if (value == null || value.length() > 15 || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))
            throw new IllegalArgumentException("IPv4は0〜255の4組で入力してください");
        long result = 0;
        for (String part : value.split("\\.")) {
            int n = Integer.parseInt(part);
            if (n > 255) throw new IllegalArgumentException("IPv4の範囲外です");
            result = result << 8 | n;
        }
        return result;
    }
    public static String format(long address) {
        return ((address >>> 24) & 255)+"."+((address >>> 16)&255)+"."+((address >>> 8)&255)+"."+(address&255);
    }
    public static long mask(int prefix) {
        if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("プレフィックスは0〜32です");
        return prefix == 0 ? 0 : (0xffffffffL << (32-prefix)) & 0xffffffffL;
    }
    public static boolean sameSubnet(String a, String b, int prefix) {
        return (parse(a)&mask(prefix)) == (parse(b)&mask(prefix));
    }
    public static String normalize(String value) { return format(parse(value)); }
}
