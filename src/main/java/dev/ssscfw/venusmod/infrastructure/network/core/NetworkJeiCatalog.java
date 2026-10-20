package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 公開ガイド専用の不変カタログ。所有権や実ワールドの設定を読むことはない。 */
public final class NetworkJeiCatalog {
    public record Spec(String id,String title,List<String> items,List<String> lines,Set<String> topics) {
        public Spec {items=List.copyOf(items);lines=List.copyOf(lines);topics=Set.copyOf(topics);}
    }
    private static final List<String> COLORS=List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black");
    private static final List<Spec> SPECS=build();
    private static final Map<String,Spec> ITEMS=index();
    private NetworkJeiCatalog() {}
    public static List<Spec> specs(){return SPECS;}
    public static Optional<Spec> find(String item){return Optional.ofNullable(ITEMS.get(item));}
    public static List<String> ingredients(NetworkGuide.Page page){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(var node:page.nodes())result.add(node.item());
        for(var spec:SPECS)if(spec.topics.contains(page.id()))result.addAll(spec.items);
        return List.copyOf(result);
    }
    private static Map<String,Spec> index(){Map<String,Spec> result=new LinkedHashMap<>();for(var spec:SPECS)for(String item:spec.items)if(result.put(item,spec)!=null)throw new IllegalStateException("重複ガイド: "+item);return Collections.unmodifiableMap(result);}
    private static List<Spec> build(){
        List<Spec> result=new ArrayList<>();
        for(var grade:CableGrade.values()){
            List<String> variants=new ArrayList<>();variants.add(grade.id());for(String color:COLORS)variants.add(grade.id()+"_"+color);
            result.add(new Spec(grade.id(),grade.name()+" / "+(grade.optical()?"光ファイバー":"LANケーブル"),variants,List.of(
                "最大帯域: "+CableGrade.speed(grade.bps()),"最大配線長: "+grade.maxLength()+"ブロック / 太さ4/16",
                "同一規格・同一色だけ直結。無染色は無染色同士です。染料で16色に変更できます。",
                "機器間を独立したケーブルで結びます。T字分岐は不可。分岐にはスイッチを置きます。",
                grade.optical()?"両端で port N media fiber。通常機器は10G以上の対応ポートモジュールが必要です。":"銅線ポートへ接続。標準ポートは1Gbps。上位ケーブルだけではポート上限を超えません。",
                "数値はゲーム内の仮想性能。両端ポート・距離・WAN・本体容量・故障にも制限されます。"),grade.optical()?Set.of("optical","wan"):Set.of("basic","vlan")));
        }
        add(result,"network_pc","ネットワークPC",Set.of("basic","settings_gui","dns","transfer_qos"),"IP・ゲートウェイ・DNSを設定する管理端末。","例: ip 192.168.1.10/24 / ping 192.168.1.20","仮想Web・転送・遠隔制御を操作。実ブラウザーや外部インターネットには接続しません。");
        add(result,"network_l2_switch","L2スイッチ",Set.of("basic","vlan","stp","redundancy"),"同じLANの通信を中継し、複数端末を別ポートへ接続します。","Access/Trunk、許可VLAN、simple/Classic STPに対応。","VLAN間通信にはL3機器とIP経路が必要です。");
        add(result,"network_l3_switch","L3スイッチ",Set.of("routing","acl","ospf","vlan","upgrades"),"L2中継に加え、ポートのIPと経路で異なるLANを接続します。","初期版は6面ポート。8/24ポート筐体ではありません。","端末のゲートウェイと返信経路も設定してください。");
        add(result,"network_router","ネットワークルーター",Set.of("routing","nat","ospf","bgp","vrrp","acl","dhcp_relay"),"LAN間の経路選択、NAT、DHCP、OSPF/BGP、VRRPを設定します。","初期P0=192.168.1.1/24、P1=192.168.2.1/24。","物理ポート・IP・戻りの経路を確認。名前だけで経路は成立しません。");
        for(String id:List.of("network_onu","network_olt"))add(result,id,id.endsWith("onu")?"ONU / 光回線終端装置":"OLT / 光回線局側装置",Set.of("optical","wan"),"北側P0は標準で光対応。ONUとOLTを光ケーブルで接続します。","ONU–OLT間の論理リンクがWAN区間。初期20Mbpsです。","両端のWAN枠・ポート・本体を強化。PON分岐や実ISP契約は未対応です。");
        add(result,"network_server","ネットワークサーバー",Set.of("services","dhcp_relay","dns","monitor","syslog","ntp","backup"),"仮想Web、DHCP/DNS、監視、Syslog、NTPなどを提供します。","サービスは必要なものだけ有効化。配線・IP・ACLを別途設定します。","公開サービスと管理権限は別。public onだけで設定や収集ログを公開しません。");
        add(result,"network_redstone_terminal","レッドストーン通信端末",Set.of("services","control-safety","monitor"),"許可された通信でレッドストーン出力を0〜15に変更します。","通信断は既定hold。control-safety stop/valueで安全値を設定できます。","本体故障と通信断は別。停止後は新しい制御命令で再開します。");
        add(result,"network_gold_circuit","金配線基板",Set.of("upgrades"),"ネットワーク機器・強化部品のクラフト材料です。","装着モジュールではありません。Rで実際のクラフトレシピを確認します。");
        add(result,"network_gold_bundle","圧縮金配線材",Set.of("upgrades","wan"),"上位強化に使用する金72インゴット分の中間材料。","説明カードはクラフトではありません。製作材料はRのレシピで確認します。");
        String[] tiers={"10g","40g","100g","400g"};
        for(int i=0;i<tiers.length;i++)add(result,"network_port_"+tiers[i],CableGrade.speed(NetworkProfile.SPEEDS[i+1])+" ポート強化",Set.of("upgrades","optical"),"左のポート専用枠P0〜P5に装着。1枠の強化は他ポートへ波及しません。","装着ポート上限: "+CableGrade.speed(NetworkProfile.SPEEDS[i+1]),"光にも対応。port N media fiber を両端に設定します。","ケーブル・対向ポート・本体容量が低ければ実効値は低下。既存故障は直りません。");
        add(result,"network_stability_50","耐久強化モジュール",Set.of("upgrades","redundancy"),"本体の中央拡張枠に装着。内部ランダム故障率を50%低減します。","停止・ポート故障・帯域劣化が対象。断線や設定ミスは防ぎません。");
        add(result,"network_stability_90","高度安定化モジュール",Set.of("upgrades","redundancy"),"本体の中央拡張枠に装着。内部ランダム故障率を90%低減します。","複数の安定化は最も高い効果を使用し、既存故障は修理しません。");
        add(result,"network_stability_perfect","完全安定化モジュール",Set.of("upgrades","redundancy"),"本体の中央拡張枠に装着。内部ランダム故障率を0%にします。","断線・外部障害・設定ミス・手動故障試験は防ぎません。既存故障は修理キットで復旧します。");
        add(result,"network_processor","転送処理モジュール",Set.of("upgrades","wan","transfer_qos"),"中央枠に1枚装着するごとに本体容量4倍、上限768Gbps。","標準容量は6Gbps。各ポートとWANの速度上限は別に強化が必要です。");
        add(result,"network_buffer","バッファモジュール",Set.of("upgrades","transfer_qos"),"中央枠に装着。送信元の同時仮想転送枠を1枚で4つ増やします（標準4）。","帯域そのものを増やすものではありません。複数転送はリンク容量を共有します。");
        for(int i=1;i<=5;i++)add(result,"network_wan_"+i,"WAN強化 "+i,Set.of("wan","upgrades"),"右側のWAN専用枠へ装着。ONU–OLT回線の両端を強化します。","回線上限: "+CableGrade.speed(NetworkProfile.WAN_SPEEDS[i]),"実効帯域は遅い側に従い、光ポート・本体処理容量・混雑にも制限されます。");
        add(result,"network_repair_kit","ネットワーク修理キット",Set.of("upgrades","redundancy"),"通常右クリック: 本体と全ポートの故障・帯域劣化を修理します。","Shift+面を右クリック: その面のポートだけ修理します。","成功時に1個消費。正常箇所や権限外では消費せず、設定や管理OFFは変えません。");
        add(result,"network_configurator","ネットワーク診断ツール",Set.of("settings_gui","basic","optical"),"機器に使用すると設定画面を開き、ケーブルに使用すると規格を表示します。","機器の所有権・共有権限が必要です。JEIのカテゴリ一覧から全ガイドも確認できます。");
        add(result,"network_goggles","ネットワークゴーグル",Set.of("settings_gui","upgrades","optical"),"頭に装備し、機器を選択すると使用/設定済みポート、IP、帯域、故障を表示します。","選択面のポートとVLAN情報、ケーブルのカテゴリー・最大帯域も確認できます。");
        add(result,"combined_network_goggles","統合ネットワークゴーグル",Set.of("settings_gui","upgrades"),"Createのエンジニアゴーグルとネットワーク表示をまとめた頭装備。","Create導入時の連携装備です。ネットワーク本体にCreateは不要です。");
        return List.copyOf(result);
    }
    private static void add(List<Spec> target,String id,String title,Set<String> topics,String... lines){target.add(new Spec(id,title,List.of(id),List.of(lines),topics));}
}
