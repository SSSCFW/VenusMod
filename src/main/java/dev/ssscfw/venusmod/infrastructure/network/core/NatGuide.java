package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** GUIとJEIに同じNATの接続図・操作例を提供する。 */
public final class NatGuide {
    private NatGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("nat","NAT/NAPTとポート転送",
        List.of(new NetworkGuide.Node("network_pc","LAN-PC",8,0),new NetworkGuide.Node("network_server","LAN-Web",8,30),new NetworkGuide.Node("network_router","NAT",80,15),new NetworkGuide.Node("network_server","外側",152,15)),
        List.of(new NetworkGuide.Edge(0,2,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(2,3,false)),
        List.of(
            step("1. 内側と外側を指定","Router P0: 192.168.10.1/24、P1: 203.0.113.1/24。","nat port 0 inside / nat port 1 outside","nat on (コマンドは1行ずつ実行)","図は論理構成。複数のLAN端末は別途L2SWでまとめます。"),
            step("2. 外向き通信を共有","PC: 192.168.10.10/24、gateway 192.168.10.1。","外側Server: 203.0.113.20/24。private側への経路は不要です。","ping 203.0.113.20 / nat sessions","NATは経路を作りません。外向きの静的経路/OSPFは別に必要です。"),
            step("3. LANのWebを公開","LAN-Web: 192.168.10.20/24、GW .1、web on。","nat forward add 1 tcp 8080 192.168.10.20 80","外側PCから open 203.0.113.1:8080","実Webではなく、許可されたゲーム内の固定サービスへ接続します。"),
            step("4. ACLと利用権限は別","入力ACLは変換前、出力ACLは変換後のIP/ポートを見ます。","外側の受信許可は8080番、内側の送信許可は80番になります。","共有は対象サーバーのtrust、またはサービスのpublic onで設定。","NAT設定だけでは他プレイヤーの管理権限を取得できません。"),
            step("5. 期限・復旧・制限","変換表のアイドル期限: TCP120秒、UDP30秒、ICMP10秒。","転送中は延長。nat clearで変換表と稼働転送をリセット。","設定は採掘で保持、変換表は再配置/再起動で再作成。","hairpin、任意公開IPの1対1NAT、ALG、VRRP状態同期は未対応。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
