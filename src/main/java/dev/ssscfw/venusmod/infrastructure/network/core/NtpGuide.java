package dev.ssscfw.venusmod.infrastructure.network.core;
import java.util.List;

/** GUI/JEI用の共通説明。外部URLを開かず既存の機器アイコンを使用する。 */
public final class NtpGuide {
    private NtpGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("ntp","NTPで機器の時計を合わせる",
        List.of(new NetworkGuide.Node("network_pc","時計を合わせる",8,10),new NetworkGuide.Node("network_l2_switch","LAN",80,10),new NetworkGuide.Node("network_server","仮想基準時計",152,10)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false)),List.of(
            step("1. 基準サーバーを用意","Server: ip 192.168.1.20/24 → ntp server on","これはゲーム内のローカル基準時計です。現実のUTC/外部NTPではありません。","NTP基準serverとclientは同時有効にできません。"),
            step("2. 同期先を設定","PC: ntp target 192.168.1.20 → ntp on","ntp interval 30 / ntp sync / ntp status","UDP123の往復・VLAN・ACL・NATと相手サービスを確認してから補正します。"),
            step("3. ずれと失敗を確認","ntp offset 5000 / ntp drift 50 は表示時計の試験用です。","通信断時は補正を維持し、未同期/古い同期を状態に表示します。","処理は1tick最大2台。時間補正でゲームや制御期限のtickは変わりません。"),
            step("4. ログと持ち運び","Syslogは発生時計/受信時計と単調tickを別々に記録します。","時計が戻っても過去ログの時刻・到着順序は書き換えません。","設定は採掘で保持。動的な補正と同期状態は再配置後に再確立します。")
        ));}
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
