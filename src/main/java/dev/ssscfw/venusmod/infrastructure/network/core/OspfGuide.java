package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** GUI/JEI共通。既存テクスチャのルーターを使い、余分な画像読込みを増やさない。 */
public final class OspfGuide {
    private OspfGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("ospf","OSPFで経路を自動学習",
        List.of(new NetworkGuide.Node("network_router","R-A",8,0),new NetworkGuide.Node("network_router","R-B",80,32),new NetworkGuide.Node("network_router","R-C",152,0)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(0,2,true)),List.of(
        step("1. 同じエリアで有効化","全ルーター: ospf area 0 / ospf on","ospf router-id 1.1.1.1 (他は2.2.2.2、3.3.3.3)","Router IDは重複不可。実際のポートIPは別途設定します。"),
        step("2. 参加ポートとコスト","ルーター間: ospf port 1 on","端末側: ospf port 0 passive","ospf cost 1 10。最小合計コストの経路を選びます。"),
        step("3. 隣接と広告を確認","ospf neighbors / ospf database / show ip route","Hello交換→INIT→EXCHANGE→FULL。既定Hello1秒/Dead4秒。","同じLAN・Area・Hello/Deadが必要。ACLでospfも許可します。"),
        step("4. 故障・復旧と再現範囲","予備経路を張り、故障したルーターを迂回できるか確認します。","設定と静的経路は保存、学習経路とLSDBは再起動後に再学習。","この版は単一エリア内・全隣接方式。ABR/DR/BDR/ECMPは未対応。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
