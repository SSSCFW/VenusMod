package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 静的経路だけの個別編集。呼出し元はコピーを検証し、成功した変更だけを保存する。 */
public final class StaticRoutes {
    private StaticRoutes() {}

    public static NetworkProfile.Route parse(String cidr, String gateway, int port) {
        String[] parts = field(cidr).split("/", -1);
        if (parts.length != 2 || !parts[1].matches("[0-9]{1,2}"))
            throw new IllegalArgumentException("宛先はIPv4/プレフィックス（例: 192.168.20.0/24）です");
        int prefix = Integer.parseInt(parts[1]);
        String network = Ipv4.format(Ipv4.parse(parts[0]) & Ipv4.mask(prefix));
        return new NetworkProfile.Route(network, prefix, field(gateway), port);
    }

    private static String field(String value) {
        if (value == null || value.length() > StaticRouteDraft.MAX_FIELD_TEXT
                || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("経路の入力は制御文字なし64文字以内です");
        return value.strip();
    }

    /** 保存済みの旧経路がホスト部を含む場合も、意味が同じ宛先として比較する。 */
    private static boolean same(NetworkProfile.Route a, NetworkProfile.Route b) {
        return a.prefix() == b.prefix() && a.port() == b.port() && a.gateway().equals(b.gateway())
                && (Ipv4.parse(a.network()) & Ipv4.mask(a.prefix())) == Ipv4.parse(b.network());
    }

    public static boolean edit(NetworkProfile profile, String[] args) {
        if (!profile.kind.routing()) throw new IllegalArgumentException("静的経路の編集はルーター/L3スイッチのみです");
        if (args.length == 2 && args[1].equals("clear")) {
            if (profile.routes.isEmpty()) return false;
            profile.routes.clear(); return true;
        }
        if (args.length != 7 || !(args[1].equals("add") || args[1].equals("delete"))
                || !args[3].equals("via") || !args[5].equals("port"))
            throw new IllegalArgumentException("route add|delete 10.0.0.0/24 via 192.168.1.1 port 0");
        var route = parse(args[2], args[4], Integer.parseInt(args[6]));
        if (args[1].equals("delete")) return profile.routes.removeIf(existing -> same(existing, route));
        if (profile.routes.stream().anyMatch(existing -> same(existing, route))) return false;
        if (profile.routes.size() >= 64) throw new IllegalArgumentException("静的経路は64件までです");
        profile.routes.add(route); return true;
    }

    public static List<String> describe(NetworkProfile profile) {
        var lines = new java.util.ArrayList<String>();
        lines.add("静的経路 " + profile.routes.size() + "件（設定一覧・疎通成功ではありません）");
        for (var route : profile.routes) lines.add(label(parse(route.network() + "/" + route.prefix(), route.gateway(), route.port())));
        return List.copyOf(lines);
    }

    public static String label(NetworkProfile.Route route) {
        return route.network() + "/" + route.prefix() + " via " + route.gateway() + " P" + route.port();
    }
    static String command(String operation, NetworkProfile.Route route) {
        return "route " + operation + " " + route.network() + "/" + route.prefix()
                + " via " + route.gateway() + " port " + route.port();
    }
}
