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
            step("4. 停止・保存・上限","monitor off / monitor delete base / monitor clear","監視元が故障中はPAUSED。再起動/回収後はUNKNOWNから再確認します。","設定は装着品/故障/所有権と共に保持。状態は保存せず、16対象/台・全体256対象・毎tick2件まで。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
