package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 既存機器のテクスチャを使うGUI/JEI共通ガイド。 */
public final class DnsGuide {
    private DnsGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("dns","名前で設備へ接続 / DNS・TTL",
        List.of(new NetworkGuide.Node("network_pc","Client",8,12),new NetworkGuide.Node("network_l2_switch","LAN",80,12),new NetworkGuide.Node("network_server","DNS",152,0),new NetworkGuide.Node("network_server","Web",152,32)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(1,3,false)),List.of(
            step("1. DNSとIPを設定","DNSサーバー: 192.168.1.53/24、Web: 192.168.1.20/24。","DNS側: dns-record base.local 192.168.1.20","PC側: dns 192.168.1.53 → open base.local"),
            step("2. TTLで古い回答の残り方を確認","dns-record set base.local 10 192.168.1.20","PCの dns-cache に残りtickを表示。20tick=1秒です。","TTL中にDNSを編集しても、PCの回答は満了まで変わりません。"),
            step("3. 複数IPの回答順を回す","dns-record set pool.local 0 192.168.1.20 192.168.1.21","TTL0では毎回問い合わせ、先頭IPが順番に変わります。","稼働確認ではなく回答順の変更です。故障した宛先も返します。"),
            step("4. 名前なし・障害・診断を区別","nslookup base.local は毎回直接問い合わせます。","dns-cache clear で自機だけを消去。名前なしは既定10秒保存。","UDP53のACL拒否・断線・DNS停止はNXDOMAINとして保存しません。"),
            step("5. 名前解決後も接続を検査","open base.local:8080 / transfer base.local 100","名前が分かっても宛先のACL・NAT・操作権限は別に必要です。","Aレコードだけの仮想DNSです。外部DNS/ブラウザーへは接続しません。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
