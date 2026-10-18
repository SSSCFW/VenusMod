package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 定期確認は既存サーバーの機能。GUI/JEIとも既存の機器アイコンを使用する。 */
public final class MonitorGuide {
    private MonitorGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("monitor","定期監視で障害と復旧を検出",
        List.of(new NetworkGuide.Node("network_server","監視Server",8,12),new NetworkGuide.Node("network_l2_switch","LAN",80,12),new NetworkGuide.Node("network_router","対象機器",152,12)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false)),List.of(
            step("1. 監視元のサーバーを用意","監視Server: ip 192.168.1.10/24。遠いLANにはgatewayも必要です。","monitor add base 192.168.1.20 → monitor on","対象は数値IPv4。仮想ICMPが往復できるか定期確認します。外部ネットワークには出ません。"),
            step("2. 間隔と連続回数を設定","monitor interval 5 / monitor thresholds 3 2","3回連続で失敗するとDOWN、2回連続で成功するとUPになります。","1回の短い通信断では通知せず、状態変化だけlogへ記録します。"),
            step("3. 監視状態と集中ログ","監視ボタン、またはmonitor status。再実行で最新の結果を表示。","syslog target 192.168.1.30 → syslog on で変化を収集サーバーへ送れます。","DOWNは観測地点から応答なし。ACLや戻り経路も確認。機器故障と断定しません。"),
            step("4. 停止・保存・上限","monitor off / monitor delete base / monitor clear","監視元が故障中はPAUSED。再起動/回収後はUNKNOWNから再確認します。","設定は装着品/故障/所有権と共に保持。状態は保存せず、16対象/台・全体256対象・毎tick2件まで。"),
            step("5. pingでは分からないWeb停止","monitor add site web 192.168.1.20 → monitor on","pingが通ってもweb offならDOWN。TCP80の往復とサービス稼働を確認します。","HTTP本文やログは取得しません。ICMP拒否とWeb停止は別の状態です。"),
            step("6. DNS・転送・制御の入口","monitor add resolver dns 192.168.1.53 (UDP53)","monitor add files transfer 192.168.1.20 / monitor add signal control 192.168.1.30","転送開始やレッドストーン出力変更はしません。DNSレコードの正しさは別途nslookupで確認。"),
            step("7. NAT公開ポートと限界","monitor add site web 203.0.113.1:8080","NATで8080→内部80へ転送。誤内部ポート・停止・返信ACL拒否はDOWNです。","数値IPv4のみ。実TCP接続ではなくメタデータで検査。上限はICMPと共用します。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
