package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 既存の機器アイコンを使う集中ログの説明。GUI/JEIで共通の内容。 */
public final class SyslogGuide {
    private SyslogGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("syslog","Syslogで障害ログを集中管理",
        List.of(new NetworkGuide.Node("network_router","送信機器",8,12),new NetworkGuide.Node("network_l2_switch","LAN",80,12),new NetworkGuide.Node("network_server","収集Server",152,12)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false)),List.of(
            step("1. 収集サーバーのIPを設定","Server: ip 192.168.1.20/24 → syslog server on","syslog allow 192.168.1.0/24 で受信元の範囲を限定できます。","初期は受信OFF。既存ネットワークサーバーを使い、外部サーバーには接続しません。"),
            step("2. 送信機器を登録","送信機器: syslog target 192.168.1.20 → syslog on","L2スイッチ等も、ログ送信用の自機IPが必要です。","経路切替や転送など、新しく記録されたローカルログを配送します。"),
            step("3. 試験と収集ログの確認","送信機器: syslog test 3 test-message","Server: syslog logs / ログボタン。syslog status で送信/受信状態を表示。","syslog level 6 は重大度0〜6だけ送ります。既存ログの既定は6です。"),
            step("4. UDP514と障害の扱い","UDP514の片方向通信。NAT越しでも返信経路は不要です。","ACL、受信元範囲、転送先ポート514、受信サービス状態を検査します。","完全停止した自機からは送れません。隣接機器の切替ログも集めてください。"),
            step("5. 権限と保持上限","syslog clear は自機の受信履歴のみ消去。閲覧・操作には管理権限が必要。","受信履歴は256件/機器、全体4096件。再転送や失敗時の再送はしません。","設定は機器アイテムへ保存。履歴・配送待ちは保存せず、再起動・回収で消えます。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
