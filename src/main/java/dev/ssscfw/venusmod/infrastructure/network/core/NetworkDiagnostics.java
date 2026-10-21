package dev.ssscfw.venusmod.infrastructure.network.core;

/** 診断フォームから既存CLIへの、許可された操作だけの変換。実通信・状態変更は行わない。 */
public final class NetworkDiagnostics {
    public static final int MAX_TARGET = DnsNames.MAX_NAME + 7;
    public static final int MAX_SIZE_TEXT = 7;
    public static final long MAX_MEGABYTES = 1_000_000;
    public enum Action { PING, TRACE, DNS, TRANSFER, FLOWS }
    private NetworkDiagnostics() {}

    public static String command(Action action, String target, String megabytes, TransferPriority priority) {
        if (action == null) throw new IllegalArgumentException("診断操作を選択してください");
        // 表示だけの操作に宛先・転送量の入力検証を要求しない。
        if (action == Action.FLOWS) return "flows";
        String host = input(target, MAX_TARGET, "宛先");
        return switch (action) {
            case PING -> "ping " + DnsNames.target(host);
            case TRACE -> "trace " + DnsNames.target(host);
            case DNS -> "nslookup " + DnsNames.name(host);
            case TRANSFER -> {
                var endpoint = DnsNames.endpoint(host, NetworkSimulator.TRANSFER_PORT);
                String size = input(megabytes, MAX_SIZE_TEXT, "転送量");
                if (!size.matches("[0-9]{1,7}")) throw new IllegalArgumentException("転送量は整数のMBで入力してください");
                long amount = Long.parseLong(size);
                if (amount < 1 || amount > MAX_MEGABYTES) throw new IllegalArgumentException("転送量は1〜1,000,000MBです");
                if (priority == null) throw new IllegalArgumentException("転送優先度を選択してください");
                // priority/cancelという正規ホスト名を、CLIサブコマンドに誤解釈させない。
                yield "transfer " + endpoint.host() + ":" + endpoint.port() + " " + amount + " " + priority.label();
            }
            default -> throw new IllegalArgumentException("診断操作が不正です");
        };
    }

    private static String input(String value, int maximum, String label) {
        if (value == null || value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + "の長さまたは制御文字が不正です");
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + "を入力してください");
        return normalized;
    }
}
