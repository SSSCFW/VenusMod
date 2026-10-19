package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 既存の機器アイコンで、競合する仮想転送の配分をGUI/JEIへ説明する。 */
public final class TransferGuide {
    private TransferGuide(){}
    public static NetworkGuide.Page page(){
        return new NetworkGuide.Page("transfer_qos","仮想転送の帯域配分と優先度",List.of(
            new NetworkGuide.Node("network_pc","送信元",8,16),new NetworkGuide.Node("network_l2_switch","共有LAN",80,16),
            new NetworkGuide.Node("network_server","高速宛先",152,0),new NetworkGuide.Node("network_pc","低速宛先",152,32)),
            List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(1,3,false)),List.of(
                step("1. 通信が競合したら","transfer 192.168.1.20 1000 normal","transfer 192.168.1.30 1000 high","サイズは仮想MB。共有リンク・WAN・機器処理容量を越えません。"),
                step("2. 相対優先度を選ぶ","low:normal:high = 1:2:4 の重みで配分します。","高優先度がいても、低優先度の正常な転送を停止させません。","別の区間で頭打ちの転送が使わない分は他へ回します。"),
                step("3. 途中で変更・停止","flows でID・進捗・優先度・最終割当を確認します。","transfer priority ID high / transfer cancel ID","操作できるのは、現在の機器が送信元の転送だけです。"),
                step("4. 軽量化と今回の範囲","待機/完了の変化で配分し、同じ入力では計算を再利用します。","優先度と転送進捗は保存せず、再配置・再起動後は新しく開始。","仮想bulk転送のみ。実TCP再送、DSCP、制御通信のQoSは未対応です。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
