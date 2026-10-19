package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 元データや権限情報ではなく、GUI/JEI共通の静的な操作説明だけを登録する。 */
public final class BackupGuide {
    private BackupGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("backup","機器設定をバックアップ",
        List.of(new NetworkGuide.Node("network_router","設定元",8,10),new NetworkGuide.Node("network_l2_switch","LAN",80,10),new NetworkGuide.Node("network_server","保管庫",152,10)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false)),List.of(
            step("1. 変更前に控えを保存","backup save stable / backup list","手元の控えは配線不通でも使えます。8件/設定合計64KiBまで。","同名を上書きせず拒否。削除はbackup delete NAME。"),
            step("2. 確認してから復元","backup restore stable で内容を確認します。","同じ画面で30秒以内に backup confirm TOKEN を入力。","同時編集・装着能力の変化があれば再確認。別機種への復元は不可。"),
            step("3. サーバーへ保管","Server: backup server on (TCP5001)","設定元: backup send 192.168.1.20 router_before","両側の管理権限と往復通信が必要。public onだけでは許可しません。"),
            step("4. 手元へ取得してから適用","backup fetch 192.168.1.20 router_before recovered","取得だけでは設定を書き換えません。backup restore recovered で確認。","NATの公開ポートも指定可。ACL/内部5001番/応答を確認します。"),
            step("5. 保存と安全性","故障・装着品・所有権・公開状態・現在のDHCP予約は巻き戻しません。","保管庫は通常採掘/再起動で保持。制御期限・転送中フローは復元しません。","IP/Router IDの重複に注意。設定控えだけで、ワールド全体のバックアップではありません。")));
    }
    private static NetworkGuide.Step step(String title,String...lines){return new NetworkGuide.Step(title,List.of(lines));}
}
