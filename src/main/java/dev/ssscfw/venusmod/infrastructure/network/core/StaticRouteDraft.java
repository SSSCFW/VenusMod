package dev.ssscfw.venusmod.infrastructure.network.core;

/** 画面ローカルの下書きと削除確認。確認を表示しただけでは命令を送らない。 */
public final class StaticRouteDraft {
    public static final int MAX_FIELD_TEXT = 64;
    private String network = "", gateway = "";
    private int port;
    private NetworkProfile.Route pending;

    public static boolean supported(DeviceKind kind) { return kind != null && kind.routing(); }
    public String networkText() { return network; }
    public String gatewayText() { return gateway; }
    public int port() { return port; }
    public void networkText(String value) { dismissDeletion(); network = value(value); }
    public void gatewayText(String value) { dismissDeletion(); gateway = value(value); }
    public void port(int value) {
        dismissDeletion();
        if (value < 0 || value >= NetworkProfile.PORT_COUNT) throw new IllegalArgumentException("ポートは0〜5です");
        port = value;
    }
    private static String value(String text) {
        if (text == null || text.length() > MAX_FIELD_TEXT) throw new IllegalArgumentException("入力は64文字以内です");
        return text;
    }
    private NetworkProfile.Route route() { return StaticRoutes.parse(network, gateway, port); }
    public String addCommand() { dismissDeletion(); return StaticRoutes.command("add", route()); }
    public void requestDeletion() { dismissDeletion(); pending = route(); }
    public boolean deletionPending() { return pending != null; }
    public String pendingDescription() { return pending == null ? "" : StaticRoutes.label(pending); }
    public String confirmDeletion() {
        var target = pending; dismissDeletion();
        if (target == null) throw new IllegalArgumentException("削除する経路を再確認してください");
        return StaticRoutes.command("delete", target);
    }
    public void dismissDeletion() { pending = null; }
}
