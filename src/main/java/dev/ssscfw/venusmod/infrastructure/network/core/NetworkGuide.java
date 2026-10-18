package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** GUIとJEIが同じ接続図・手順を使う。機器の実設定や所有者情報を含めない。 */
public final class NetworkGuide {
    public record Node(String item,String label,int x,int y){}
    public record Edge(int a,int b,boolean standby){}
    public record Step(String title,List<String> lines){public Step{lines=List.copyOf(lines);}}
    public record Page(String id,String title,List<Node> nodes,List<Edge> edges,List<Step> steps){
        public Page{nodes=List.copyOf(nodes);edges=List.copyOf(edges);steps=List.copyOf(steps);}
    }
    public record Recipe(Page page,int step){public Recipe{if(step<0||step>=page.steps.size())throw new IllegalArgumentException("guide step");}}
    private static final List<Page> BASE_PAGES=List.of(
        page("basic","LANの基本接続",chain("network_pc","PC-A","network_l2_switch","L2SW","network_pc","PC-B"),
            step("1. 独立したケーブルで接続","PC-AとL2SW、L2SWとPC-Bを別々のCat5eケーブルで接続します。","途中のT字分岐は不可。配線に分岐がある場合はスイッチを置きます。"),
            step("2. IPを設定","PC-A: ip 192.168.1.10/24","PC-B: ip 192.168.1.20/24","機器を右クリックしてコンソールへ入力します。"),
            step("3. 往復の通信を確認","PC-A: ping 192.168.1.20","mac / arp で学習した情報、ports でリンク状態を確認できます。","機器は6面ポート。0北・1東・2南・3西・4上・5下です。")),
        page("vlan","VLANでLANを分離",chain("network_pc","VLAN10","network_l2_switch","SW-A","network_l2_switch","SW-B","network_pc","VLAN10"),
            step("1. PC側をAccessにする","スイッチのPC側: port 0 vlan 10","port 0 mode access","PC側のフレームにタグを付けなくても、スイッチ内ではVLAN10です。"),
            step("2. スイッチ間をTrunkにする","両スイッチの接続側: port 1 mode trunk","初期native VLANは1。VLAN10はタグ付きで中継します。","ポート番号は実際に配線した面に読み替えてください。"),
            step("3. 別VLAN間にはL3が必要","別VLANは、同じスイッチにつないでもそのままでは通信できません。","VLANを越える場合はL3スイッチ／ルーターとゲートウェイ設定が必要です。")),
        page("routing","ルーターでLANを接続",chain("network_pc","LAN-A","network_router","Router","network_server","LAN-B"),
            step("1. 2つのLAN側IP","Router: port 0 ip 10.0.1.1/24","Router: port 1 ip 10.0.2.1/24","初期ルーターの自動DHCPを止めるときは dhcp-server off。"),
            step("2. 両端に戻りの経路","PC: ip 10.0.1.10/24","PC: gateway 10.0.1.1","Serverは10.0.2.10/24、gateway 10.0.2.1を指定します。"),
            step("3. 遠くのLANへの静的経路","route add 10.0.3.0/24 via 10.0.2.2 port 1","routes で一覧を確認。相手側にも戻りの経路が必要です。","OSPFとBGPの自動学習は各プロトコルのページを参照してください。")),
        page("optical","光ファイバーを接続",chain("network_l2_switch","光ポート","om3_fiber_cable","OM3","network_l2_switch","光ポート"),
            step("1. 対応モジュールを装着","通常機器の光接続には10G以上のポートモジュールが必要です。","GUI左のP0〜P5で、配線する面の専用枠に装着します。"),
            step("2. 両端を光に変更","port 0 media fiber","銅線に戻す場合: port 0 media copper","ONU/OLTのP0は標準で光対応。光Ethernet同士の接続にONUは不要です。"),
            step("3. 規格と距離を確認","Cat5eは最大64ブロック、OM3は256、OS2は4096です。","上限はゲーム用の値。診断ツールでケーブルの規格を確認できます。","太さはすべて4/16。異なる種類を直結せず、中継機器を挟みます。")),
        page("wan","遅いWANを高速化",chain("network_router","拠点LAN","network_onu","ONU","network_olt","OLT","network_router","遠隔LAN"),
            step("1. WAN区間","この版ではONUとOLTの間の論理リンクにWAN制限を適用します。","初期20Mbps。その他のLANリンクにはWAN制限を付けません。","実インターネットへは接続しません。"),
            step("2. 両端のWAN枠を強化","ONU/OLTの右側WAN枠へ同等グレードを装着します。","100Mbps→1→10→100→400Gbps。遅い側が上限になります。","ISP契約・光スプリッタ・惑星間接続はまだ未対応です。"),
            step("3. ボトルネックを残さない","400Gbps回線でも手前が1Gbpsポートなら1Gbps以下です。","ケーブル・両端ポート・本体処理容量を合わせて強化してください。","transfer IP 100 と flows で仮想100MB転送を確認できます。")),
        page("upgrades","ポートと本体を強化",chain("network_port_10g","ポート枠","network_l3_switch","本体","network_processor","処理容量"),
            step("1. 各ポートは独立","左6枠はポート専用。1枠を強化しても他のポートは変わりません。","port 0 speed auto","固定速度を設定する場合は対向側と一致させます。"),
            step("2. 本体容量と故障対策","中央6枠で処理容量・バッファ・安定化を強化します。","処理モジュール1枚で本体容量4倍。完全安定化で内部ランダム故障率0%。","外部の断線・設定ミスは防ぎません。"),
            step("3. 高価な設備を持ち運ぶ","通常採掘で装着品・設定・故障・所有権を機器アイテムに保存します。","シルクタッチ不要。配線は再接続してください。","故障は置き直しても残り、修理キットで直します。")),
        new Page("redundancy","故障に備える接続",List.of(new Node("network_l2_switch","SW-A",8,0),new Node("network_l2_switch","SW-B",152,0),new Node("network_l2_switch","SW-C",80,32)),List.of(new Edge(0,1,false),new Edge(0,2,false),new Edge(1,2,true)),List.of(
            step("1. L2の予備経路","stp on","3台のスイッチを環状に接続すると、簡略STPが1経路を遮断します。","使用中の経路が切れると予備経路へ切り替わります。"),
            step("2. ゲートウェイの予備機","Router-A: vrrp 10.0.1.254 120","Router-B: vrrp 10.0.1.254 100","両方を同じLANへ接続し、各機器の実IPは別に設定します。"),
            step("3. この版での再現範囲","STPはsimple/Classic BPDU方式を選択。詳しくはSTPページを参照。","fault は自分の装置を故障させる試験操作。修理キットで復旧します。","予備機から先にも通信できる経路が必要です。"))),
        page("acl","ACLで通信を制御",chain("network_pc","Client","network_router","ACL","network_server","Server"),
            step("1. 通信を絞って許可","acl add 0 in 10 permit tcp 10.0.1.0/24 any 80","P0の入側でTCP宛先80番だけを許可します。","規則を設定した方向の未一致通信は、暗黙のdenyで停止します。"),
            step("2. 順序と方向を確認","acl","番号の小さい規則を先に検査し、最初の一致で決定します。","in=そのポートの受信、out=送信。返りの通信も別に検査します。"),
            step("3. 変更・取り外し","acl delete 0 in 10","acl clear 0 in","clearでその方向の適用を外します。マルチの機器管理権限はACLとは別です。"),
            step("4. サービス別の仮想ポート","ping=ICMP、DNS=UDP53、Web=TCP80。","transfer=TCP5000、レッドストーン制御=TCP25570。","ACLはIPメタデータの検査で、実TCPスタックやステートフルFWではありません。")),
        page("dhcp_relay","別LANからDHCPを利用",chain("network_pc","LAN-A","network_router","Relay","network_server","DHCP"),
            step("1. LANと戻りの経路","Relay P0: 10.0.1.1/24、P1: 10.0.2.1/24。","DHCP Server: 10.0.2.10/24、gateway 10.0.2.1。","Relayの dhcp-server off、Serverの dhcp-server on を設定します。"),
            step("2. LAN-A用のプール","Server:","dhcp-pool add 10.0.1.0/24 10.0.1.100 10.0.1.150 10.0.1.1 10.0.2.10","順にサブネット、開始、終了、GW、DNSです。"),
            step("3. クライアント側で中継","Relay: dhcp-relay 0 10.0.2.10","Client: dhcp","giaddrはLAN-A側の10.0.1.1。Server側のLANではなくLAN-A用プールを選びます。"),
            step("4. 配布失敗の確認","UDP67の往復経路とACL、LAN側UDP68の応答を確認します。","dhcp-pool / dhcp-relay / dhcp-bindings で設定と予約を確認。","この版は手動要求・期限なし予約。不要な予約の解除は dhcp-bindings clear。")),
        page("services","ゲーム内設備を操作",new Diagram(List.of(new Node("network_pc","Client",8,10),new Node("network_l2_switch","LAN",80,10),new Node("network_server","Web",152,0),new Node("network_redstone_terminal","Signal",152,32)),List.of(new Edge(0,1,false),new Edge(1,2,false),new Edge(1,3,false))),
            step("1. 仮想Web画面","Server: web on","Client: open 192.168.1.20","固定の管理画面だけを表示。ブラウザー・JavaScript・外部HTTPは使いません。"),
            step("2. レッドストーン出力","端末にIPを設定し、PCで redstone 192.168.1.30 15","通信と権限を確認して出力を変更します。0〜15を指定できます。","図は構成の概念図です。各端末はスイッチの別ポートへつなぎます。"),
            step("3. 他のプレイヤーと共有","trust プレイヤー名 / untrust プレイヤー名","オンラインのプレイヤーに管理権限を共有します。","public on は遠隔サービスだけを公開し、設定画面は公開しません。"))
    );
    private static final List<Page> PAGES=java.util.stream.Stream.concat(BASE_PAGES.stream(),java.util.stream.Stream.of(OspfGuide.page(),NatGuide.page(),VrrpGuide.page(),StpGuide.page(),BgpGuide.page(),DnsGuide.page())).toList();
    private NetworkGuide(){}
    public static List<Page> pages(){return PAGES;}
    public static List<Recipe> recipes(){List<Recipe> result=new ArrayList<>();for(Page page:PAGES)for(int i=0;i<page.steps.size();i++)result.add(new Recipe(page,i));return List.copyOf(result);}
    public static int initialPage(DeviceKind kind){String id=switch(kind){case PC,L2_SWITCH->"basic";case L3_SWITCH,ROUTER->"routing";case ONU,OLT->"wan";case SERVER->"dhcp_relay";case REDSTONE_TERMINAL->"services";};for(int i=0;i<PAGES.size();i++)if(PAGES.get(i).id.equals(id))return i;return 0;}
    private record Diagram(List<Node> nodes,List<Edge> edges){}
    private static Diagram chain(String... pairs){
        int count=pairs.length/2;List<Node> nodes=new ArrayList<>();List<Edge> edges=new ArrayList<>();
        for(int i=0;i<count;i++){nodes.add(new Node(pairs[i*2],pairs[i*2+1],8+i*144/(count-1),10));if(i>0)edges.add(new Edge(i-1,i,false));}return new Diagram(nodes,edges);
    }
    private static Page page(String id,String title,Diagram diagram,Step... steps){return new Page(id,title,diagram.nodes,diagram.edges,List.of(steps));}
    private static Step step(String title,String... lines){return new Step(title,List.of(lines));}
}
