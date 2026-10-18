package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 監視と区別した制御更新・安全出力。GUI/JEIは同じ静的な説明を使う。 */
public final class ControlSafetyGuide {
    private ControlSafetyGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("control-safety","通信断時の安全出力",
        List.of(new NetworkGuide.Node("network_pc","制御元",8,12),new NetworkGuide.Node("network_l2_switch","LAN",80,12),new NetworkGuide.Node("network_redstone_terminal","出力端末",152,12)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false)),List.of(
            step("1. 出力端末で安全方針を設定","端末: ip 192.168.1.20/24 → control-safety stop 5","更新が5秒届かない場合は出力0。control-safety value 7 5なら指定値7へ移行します。","既定control-safety holdは従来の保持。安全方針を有効にすると最初は安全値です。"),
            step("2. 手動命令と継続更新","制御元: redstone 192.168.1.20 15 (単発。設定期限後に安全値)","redstone maintain 192.168.1.20 15 で20tickごとに更新を送ります。","継続更新は期限3秒以上。成功した制御通信だけが期限を延長し、監視のUPでは延長しません。"),
            step("3. 断線と復旧","control-statusで実出力・期限を確認。設定や状態を読んでも期限は延長しません。","切断・ACL拒否で更新が途切れると、期限後に安全値へ移行します。","配線を直しただけでは古い出力を自動再開しません。新しいredstone命令で明示的に復旧。"),
            step("4. 更新停止と権限","制御元でredstone links / redstone stop。最後に届いた更新の期限はそのままです。","送信元/受信先の所有者・明示共有・受信サービス公開を毎回確認。OPだけで継続許可を作りません。","名前は開始時にIPへ解決し、受信機器IDも固定。NAT転送先が別機器へ変わっても制御しません。"),
            step("5. 持ち運び・故障・上限","安全方針は装着品・故障・所有者と一緒に保持。期限と継続ジョブは保存しません。","再ロード/再配置は安全値。機器本体の故障中は出力0、修理だけで以前の出力は復活しません。","1次元128期限/64維持・送信元4維持、通信確認は毎tick2件。保持モードは従来どおりです。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
