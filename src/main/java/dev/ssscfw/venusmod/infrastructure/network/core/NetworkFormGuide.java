package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 機器フォームの説明もJEI/機器GUIで共通管理。既存の機器テクスチャを利用する。 */
public final class NetworkFormGuide {
    private NetworkFormGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("settings_gui","クリックで基本・ポートを設定",
        List.of(new NetworkGuide.Node("network_pc","基本設定",8,12),new NetworkGuide.Node("network_l2_switch","VLAN/速度",80,12),new NetworkGuide.Node("network_router","ポートIP",152,12)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false)),List.of(
            step("1. 設定フォームを開く","機器を右クリックし「設定」を押すと基本設定のフォームを開きます。","「ポート」またはフォーム上のP0〜P5で、配線した面の設定を選べます。","P0北/P1東/P2南/P3西/P4上/P5下。正面を回しても世界方位は変わりません。"),
            step("2. IPと各ポートを編集","端末のIPv4/プレフィックス/GW/DNSは基本設定で入力します。","ルーター/L3SWのIPv4はポートごとに指定。VLANは数値、速度/媒体/Access-Trunkはクリックで選択。","空のIP欄は未設定。高い速度と光接続には対応する装着モジュールが必要です。"),
            step("3. 適用・再読込・同時編集","「適用」は表示中のページだけを一括検証して保存します。","他の人が同じ項目を変更した場合は上書きしません。「再読込」で現在値を確認してください。","周期同期とページ切替では未保存入力を消しません。別ポートやACLの編集は保持します。"),
            step("4. コンソールとの併用","詳細なACL/OSPF/BGP/NAT設定と通信診断は従来のCLI/ガイドを使います。","フォームは同じ永続設定を編集します。故障・装着品・所有権は変更しません。","「戻る」だけでは保存しません。送信済みの適用は取り消せません。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
