import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** 独自ネットワーク機器の全リソース。IC2の画像は流用せず細い形状だけを参考にする。 */
public final class NetworkAssetGenerator {
    private static Path out;
    private static final Map<String,String> JA=new LinkedHashMap<>(),EN=new LinkedHashMap<>();
    private static final String[] DIRS={"north","east","south","west","up","down"};
    private static final String[] CABLES={"cat5e_cable","cat6_cable","cat6a_cable","cat8_cable","om3_fiber_cable","om4_fiber_cable","os2_fiber_cable","venus_fiber_cable"};
    private static final String[] CABLE_NAMES={"Cat5e LANケーブル","Cat6 LANケーブル","Cat6A LANケーブル","Cat8 LANケーブル","OM3 光ファイバー","OM4 光ファイバー","OS2 光ファイバー","金星特殊光ファイバー"};
    private static final String[] CABLE_EN_NAMES={"Cat5e LAN Cable","Cat6 LAN Cable","Cat6A LAN Cable","Cat8 LAN Cable","OM3 Optical Fiber","OM4 Optical Fiber","OS2 Optical Fiber","Venus Optical Fiber"};
    /** DyeColor.values() と同じ16色。各色の元テクスチャ/モデル/レシピを生成する。 */
    private static final String[] DYE_IDS={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
    private static final String[] JA_DYE_NAMES={"白色","橙色","赤紫色","空色","黄色","黄緑色","桃色","灰色","薄灰色","青緑色","紫色","青色","茶色","緑色","赤色","黒色"};
    private static final String[] EN_DYE_NAMES={"White","Orange","Magenta","Light Blue","Yellow","Lime","Pink","Gray","Light Gray","Cyan","Purple","Blue","Brown","Green","Red","Black"};
    private static final int[] DYE_RGB={0xF9FFFE,0xF9801D,0xC74EBD,0x3AB3DA,0xFED83D,0x80C71F,0xF38BAA,0x474F52,0x9D9D97,0x169C9C,0x8932B8,0x3C44AA,0x835432,0x5E7C16,0xB02E26,0x1D1D21};
    private static final String[] DEVICES={"network_pc","network_l2_switch","network_l3_switch","network_router","network_onu","network_olt","network_server","network_redstone_terminal"};
    private static final String[] DEVICE_NAMES={"ネットワークPC","L2スイッチ","L3スイッチ","ネットワークルーター","光回線終端装置 (ONU)","光回線局側装置 (OLT)","ネットワークサーバー","レッドストーン通信端末"};
    private static final String[] MODULES={"network_gold_circuit","network_gold_bundle","network_port_10g","network_port_40g","network_port_100g","network_port_400g","network_stability_50","network_stability_90","network_stability_perfect","network_processor","network_buffer","network_wan_1","network_wan_2","network_wan_3","network_wan_4","network_wan_5","network_repair_kit","network_configurator"};
    private static final String[] MODULE_NAMES={"金配線基板","圧縮金配線材 (金72個分)","10Gポートモジュール","40Gポートモジュール","100Gポートモジュール","400Gポートモジュール","耐久強化モジュール","高度安定化モジュール","完全安定化モジュール","転送処理モジュール","バッファモジュール","WAN強化I (100Mbps)","WAN強化II (1Gbps)","WAN強化III (10Gbps)","WAN強化IV (100Gbps)","WAN究極強化 (400Gbps)","ネットワーク修理キット","ネットワーク診断ツール"};
    private static final int[] COLORS={0x898e96,0x3b72b2,0x3b9a94,0x8b4b9f,0x34bbc1,0xbf67c6,0xe2c144,0xe9932b};
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("output directory required");out=Path.of(args[0]);Files.createDirectories(out);
        for(int i=0;i<CABLES.length;i++)cable(i);
        for(int i=0;i<DEVICES.length;i++)device(i);
        for(int i=0;i<MODULES.length;i++)module(i);
        recipes();
        JA.put("tooltip.venusmod.network.max_bandwidth","最大伝送帯域: %s");
        EN.put("tooltip.venusmod.network.max_bandwidth","Maximum bandwidth: %s");
        JA.put("tooltip.venusmod.network.max_length","最大配線長: %sブロック");
        EN.put("tooltip.venusmod.network.max_length","Maximum cable length: %s blocks");
        JA.put("tooltip.venusmod.network.cable_color_rule","同一規格・同一色のケーブルだけ接続（無染色は無染色同士）");
        EN.put("tooltip.venusmod.network.cable_color_rule","Connects only to the same grade and color (undyed to undyed).");
        // 追加の言語名前空間を使い、既存VenusModの翻訳ファイルを上書きしない。
        json("assets/venusmod_network/lang/ja_jp.json",JA);json("assets/venusmod_network/lang/en_us.json",EN);
        System.out.println("Generated network assets: "+(CABLES.length*(DYE_IDS.length+1))+" cables, 8 devices, 18 items");
    }
    private static void language(String kind,String id,String ja){JA.put(kind+".venusmod."+id,ja);EN.put(kind+".venusmod."+id,id.replace('_',' '));}
    private static Object box(double[] from,double[] to,String texture){Map<String,Object> faces=new LinkedHashMap<>();for(String d:DIRS)faces.put(d,Map.of("texture",texture,"uv",List.of(0,0,16,16)));return Map.of("from",from,"to",to,"faces",faces);}
    private static Map<String,Object> model(Map<String,String> textures,List<Object> elements){return new LinkedHashMap<>(Map.of("parent","minecraft:block/block","ambientocclusion",false,"textures",textures,"elements",elements));}
    private static String blockTex(String name){return "venusmod:block/network/"+name;}
    private static String modelId(String name){return "venusmod:block/network/"+name;}
    private static void cable(int i)throws IOException{
        cableVariant(CABLES[i],CABLE_NAMES[i],CABLE_EN_NAMES[i],COLORS[i]);
        for(int dye=0;dye<DYE_IDS.length;dye++)
            cableVariant(CABLES[i]+"_"+DYE_IDS[dye],JA_DYE_NAMES[dye]+"の"+CABLE_NAMES[i],EN_DYE_NAMES[dye]+" "+CABLE_EN_NAMES[i],DYE_RGB[dye]);
    }
    private static void cableVariant(String id,String jaName,String enName,int rgb)throws IOException{
        language("block",id,jaName);
        EN.put("block.venusmod."+id,enName);
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(new Color(rgb));g.fillRect(0,0,16,16);
        g.setColor(new Color(shade(rgb,0.55)));g.fillRect(0,0,2,16);g.fillRect(14,0,2,16);g.setColor(new Color(shade(rgb,1.3)));g.fillRect(3,0,2,16);
        g.setColor(new Color(0xdbb050));g.fillRect(0,3,16,1);g.fillRect(0,12,16,1);g.dispose();png("block/network/"+id,image);
        var textures=Map.of("cable",blockTex(id),"particle",blockTex(id));List<Object> parts=new ArrayList<>();
        Object core=box(new double[]{6,6,6},new double[]{10,10,10},"#cable");
        json("assets/venusmod/models/block/network/"+id+"_core.json",model(textures,List.of(core)));parts.add(Map.of("apply",Map.of("model",modelId(id+"_core"))));
        double[][] starts={{6,6,0},{10,6,6},{6,6,10},{0,6,6},{6,10,6},{6,0,6}};
        double[][] ends={{10,10,6},{16,10,10},{10,10,16},{6,10,10},{10,16,10},{10,6,10}};
        for(int d=0;d<6;d++){json("assets/venusmod/models/block/network/"+id+"_"+DIRS[d]+".json",model(textures,List.of(box(starts[d],ends[d],"#cable"))));parts.add(Map.of("when",Map.of(DIRS[d],"true"),"apply",Map.of("model",modelId(id+"_"+DIRS[d]))));}
        json("assets/venusmod/blockstates/"+id+".json",Map.of("multipart",parts));
        var item=model(textures,List.of(core,box(starts[0],ends[0],"#cable"),box(starts[2],ends[2],"#cable")));
        item.put("parent","minecraft:block/block");item.put("display",Map.of("gui",Map.of("rotation",List.of(30,45,0),"scale",List.of(1.0,1.0,1.0))));json("assets/venusmod/models/item/"+id+".json",item);loot(id);
    }

    // 8機種でシルエット・前面パネルを個別に設計。既存の6方向ポートと故障表示は維持する。
    private static final int[] DEVICE_HUES={
        0x5593c9,0x46b193,0x8c9fdf,0xdbab4d,0xd6e5ed,0xb2a4ed,0x6b99af,0xe66a6d
    };
    private static final String[] DEVICE_JA={
        "仮想ネットワークでpingやデータ転送を行う端末。",
        "同じLAN内の通信をMACアドレスに基づいて中継する。",
        "LAN内の転送と、異なるIPネットワーク間の経路選択を行う。",
        "ネットワーク間を接続するルーター。DHCPやNATも設定できる。",
        "光ファイバーでOLTとつなぎ、LAN側へ接続する終端装置。",
        "ONUとの光接続を収容し、仮想WANを構成する局側装置。",
        "ゲーム内Webなどのネットワークサービスを提供する。",
        "ネットワーク経由でレッドストーン信号を遠隔出力する。"
    };
    private static final String[] DEVICE_EN={
        "Terminal for ping, transfers and other simulated network commands.",
        "Forwards traffic between devices on the same LAN using MAC addresses.",
        "Switches LAN traffic and routes between IP networks.",
        "Connects networks; supports configurable DHCP and NAT.",
        "Optical network unit linking an OLT to the local LAN.",
        "Optical line terminal connecting ONUs over a simulated WAN.",
        "Provides in-game network services such as a simulated web service.",
        "Remotely outputs a redstone signal over the simulated network."
    };
    private static final String[] MODULE_JA={
        "機器や強化モジュールの製作に使う基板。",
        "高速ポートやWAN強化に使う高密度の金配線。",
        "ポート専用枠に装着。最大10Gbpsに対応。",
        "ポート専用枠に装着。最大40Gbpsに対応。",
        "ポート専用枠に装着。最大100Gbpsに対応。",
        "ポート専用枠に装着。最大400Gbpsに対応。",
        "本体拡張枠で内部ランダム故障率を50%軽減。",
        "本体拡張枠で内部ランダム故障率を90%軽減。",
        "本体拡張枠で内部ランダム故障率を0%にする。断線は防がない。",
        "本体拡張枠で転送処理容量を4倍にする（最大768Gbps）。",
        "本体拡張枠で同時転送枠を4つ追加。",
        "WAN枠に装着しONU–OLT間の帯域を100Mbpsに拡張。",
        "WAN枠に装着しONU–OLT間の帯域を1Gbpsに拡張。",
        "WAN枠に装着しONU–OLT間の帯域を10Gbpsに拡張。",
        "WAN枠に装着しONU–OLT間の帯域を100Gbpsに拡張。",
        "WAN枠に装着しONU–OLT間の帯域を400Gbpsに拡張。",
        "故障した自分の機器を右クリックして修理する。",
        "機器の設定画面を開き、ケーブルの規格を調べる。"
    };
    private static final String[] MODULE_EN={
        "Circuit board used to craft devices and upgrades.",
        "Dense gold wiring for advanced port and WAN upgrades.",
        "Port-slot upgrade: up to 10 Gbps.",
        "Port-slot upgrade: up to 40 Gbps.",
        "Port-slot upgrade: up to 100 Gbps.",
        "Port-slot upgrade: up to 400 Gbps.",
        "Chassis upgrade: 50% fewer random internal failures.",
        "Chassis upgrade: 90% fewer random internal failures.",
        "Chassis upgrade: no random internal failures; cables can still break.",
        "Chassis upgrade: 4x processing capacity, capped at 768 Gbps.",
        "Chassis upgrade: four more simultaneous transfers.",
        "WAN slot: up to 100 Mbps on ONU–OLT links.",
        "WAN slot: up to 1 Gbps on ONU–OLT links.",
        "WAN slot: up to 10 Gbps on ONU–OLT links.",
        "WAN slot: up to 100 Gbps on ONU–OLT links.",
        "WAN slot: up to 400 Gbps on ONU–OLT links.",
        "Right-click a failed network device you own to repair it.",
        "Right-click network devices to configure or inspect cables."
    };
    private static int shade(int rgb,double factor){int r=Math.min(255,(int)(((rgb>>16)&255)*factor)),g=Math.min(255,(int)(((rgb>>8)&255)*factor)),b=Math.min(255,(int)((rgb&255)*factor));return r<<16|g<<8|b;}
    private static void faceTexture(int i,String face,String id)throws IOException{
        BufferedImage img=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        int hue=DEVICE_HUES[i];
        g.setColor(new Color(i==4?0xb7ccd3:i==6?0x243541:0x35414e));
        g.fillRect(0,0,16,16);
        g.setColor(new Color(hue));g.drawRect(0,0,15,15);g.fillRect(1,1,14,2);
        if(face.startsWith("front")){
            g.setColor(new Color(0x0e1a25));
            switch(i){
                case 0 -> { // 液晶モニター
                    g.fillRect(2,4,12,9);g.setColor(new Color(0x54afd6));g.fillRect(3,5,10,7);
                    g.setColor(new Color(0xdaf6ff));g.drawLine(4,6,10,6);g.drawLine(4,8,8,8);
                }
                case 1 -> { // 一列のLANポート
                    for(int x=2;x<14;x+=3){g.fillRect(x,5,2,3);g.fillRect(x,10,2,2);}
                    g.setColor(new Color(0x67e99b));for(int x=2;x<14;x+=3)g.fillRect(x,4,2,1);
                }
                case 2 -> { // 二段のスイッチポート
                    for(int y=4;y<=9;y+=5)for(int x=2;x<14;x+=3)g.fillRect(x,y,2,3);
                    g.setColor(new Color(0xf4d06d));g.drawLine(2,8,13,8);
                }
                case 3 -> { // ルーターの状態灯
                    g.fillRect(2,6,12,5);g.setColor(new Color(0xe4af49));
                    for(int x=3;x<13;x+=3)g.fillRect(x,7,2,2);
                    g.setColor(new Color(0x72ddaa));g.drawLine(3,12,12,12);
                }
                case 4 -> { // ONUの光ポート
                    g.fillRect(3,4,10,9);g.setColor(new Color(0x50d9e5));g.fillOval(5,5,6,6);
                    g.setColor(new Color(0xe8fcfc));g.fillOval(7,7,2,2);
                }
                case 5 -> { // OLTの光モジュールスロット
                    for(int y=4;y<=10;y+=3)for(int x=2;x<14;x+=3)g.fillRect(x,y,2,2);
                    g.setColor(new Color(0xc1afff));g.fillRect(2,13,12,1);
                }
                case 6 -> { // サーバーのドライブトレイ
                    for(int y=4;y<=10;y+=3){g.fillRect(2,y,12,2);g.setColor(new Color(0x74ec9d));
                        g.fillRect(11,y,1,1);g.setColor(new Color(0x0e1a25));}
                }
                case 7 -> { // 赤い信号表示
                    g.fillRect(2,4,12,9);g.setColor(new Color(0xf26062));
                    for(int x=3;x<13;x+=3)for(int y=5;y<12;y+=3)g.fillRect(x,y,2,2);
                }
                default -> throw new IllegalArgumentException("device "+i);
            }
            g.setColor(new Color(face.equals("front_fault")?0xff453c:0x7df087));g.fillRect(12,2,2,2);
        }else if(face.equals("port")){
            g.setColor(new Color(0x161c26));g.fillRect(3,3,10,10);
            g.setColor(new Color(hue));g.drawRect(2,2,11,11);
            g.setColor(new Color(0xeac577));for(int x=4;x<12;x+=2)g.fillRect(x,9,1,3);
        }else if(face.equals("top")){
            g.setColor(new Color(0x1b2731));g.fillRect(3,4,10,8);
            g.setColor(new Color(hue));for(int x=4;x<13;x+=3)g.fillRect(x,5,2,6);
        }else if(face.equals("side")){
            g.setColor(new Color(hue));g.fillRect(2,3,3,10);
            g.setColor(new Color(0x17212d));for(int y=4;y<13;y+=2)g.fillRect(7,y,6,1);
        }else{
            g.setColor(new Color(0x1d2630));g.fillRect(3,3,10,10);
            g.setColor(new Color(hue));g.drawRect(4,4,7,7);
        }
        g.dispose();png("block/network/"+id+"_"+face,img);
    }
    private static void addPart(List<Object> parts,double x0,double y0,double z0,
                                double x1,double y1,double z1,String texture){
        parts.add(box(new double[]{x0,y0,z0},new double[]{x1,y1,z1},texture));
    }
    private static void addShell(List<Object> parts,int x0,int y0,int z0,int x1,int y1,int z1){
        Map<String,Object> faces=new LinkedHashMap<>();
        for(String d:DIRS)faces.put(d,Map.of("texture",switch(d){
            case "north"->"#front";
            case "up"->"#top";
            case "down"->"#bottom";
            default->"#side";
        }));
        parts.add(Map.of("from",List.of(x0,y0,z0),"to",List.of(x1,y1,z1),"faces",faces));
    }
    private static List<Object> deviceParts(int i){
        List<Object> parts=new ArrayList<>();
        switch(i){
            case 0 -> { // PC: モニターとキーボードとタワー
                addShell(parts,2,1,3,14,4,14);addShell(parts,3,5,8,12,13,13);
                addPart(parts,3,6,7,12,12,8,"#front");
                addShell(parts,11,1,10,15,14,15);addPart(parts,3,1,1,12,2,6,"#top");
            }
            case 1 -> { // L2: 薄い単段スイッチ
                addShell(parts,1,3,2,15,8,14);
                for(int x=2;x<=11;x+=3)addPart(parts,x,4,1,x+2,6,2,"#port");
                addPart(parts,3,8,5,13,9,12,"#top");
            }
            case 2 -> { // L3: 二段ラック
                addShell(parts,1,1,2,15,11,14);
                addPart(parts,2,3,1,14,5,2,"#front");
                addPart(parts,2,8,1,14,10,2,"#front");
                addPart(parts,4,11,5,12,12,12,"#top");
            }
            case 3 -> { // ルーター: 二本のアンテナ
                addShell(parts,2,2,2,14,9,14);
                addPart(parts,3,9,11,4,16,13,"#side");
                addPart(parts,12,9,11,13,16,13,"#side");
                addPart(parts,4,5,1,12,8,2,"#front");
            }
            case 4 -> { // ONU: 小型筐体・光コネクタ
                addShell(parts,3,2,3,13,10,13);
                addPart(parts,5,4,2,11,8,3,"#front");
                addPart(parts,7,10,7,9,13,10,"#port");
            }
            case 5 -> { // OLT: 光ポートの三段ラック
                addShell(parts,1,1,2,15,15,14);
                for(int y=3;y<=11;y+=4)addPart(parts,2,y,1,14,y+2,2,"#port");
                addPart(parts,2,1,2,3,15,14,"#side");
                addPart(parts,13,1,2,14,15,14,"#side");
            }
            case 6 -> { // サーバー: 高い筐体とドライブベイ
                addShell(parts,2,0,3,14,16,14);
                for(int y=3;y<=11;y+=3)addPart(parts,3,y,2,13,y+2,3,"#front");
                addPart(parts,12,0,3,14,16,14,"#side");
            }
            case 7 -> { // レッドストーン端末: 信号塔
                addShell(parts,2,1,2,14,7,14);
                addPart(parts,4,3,1,12,6,2,"#front");
                addPart(parts,6,7,6,10,14,10,"#port");
                addPart(parts,5,13,5,11,15,11,"#front");
            }
            default -> throw new IllegalArgumentException("device "+i);
        }
        // 論理ポートは既存どおり東西南北上下の6面。
        addPart(parts,6,6,0,10,10,1,"#port");
        addPart(parts,6,6,15,10,10,16,"#port");
        addPart(parts,0,6,6,1,10,10,"#port");
        addPart(parts,15,6,6,16,10,10,"#port");
        addPart(parts,6,13,6,10,16,10,"#port");
        addPart(parts,6,0,6,10,1,10,"#port");
        return parts;
    }
    private static void device(int i)throws IOException{
        String id=DEVICES[i];language("block",id,DEVICE_NAMES[i]);
        JA.put("tooltip.venusmod.network."+id,DEVICE_JA[i]);
        EN.put("tooltip.venusmod.network."+id,DEVICE_EN[i]);
        for(String face:List.of("front","front_fault","side","top","bottom","port"))
            faceTexture(i,face,id);
        for(boolean fault:new boolean[]{false,true}){
            Map<String,String> textures=new LinkedHashMap<>();
            textures.put("front",blockTex(id+(fault?"_front_fault":"_front")));
            textures.put("side",blockTex(id+"_side"));
            textures.put("top",blockTex(id+"_top"));
            textures.put("bottom",blockTex(id+"_bottom"));
            textures.put("port",blockTex(id+"_port"));
            textures.put("particle",blockTex(id+"_side"));
            json("assets/venusmod/models/block/network/"+id+(fault?"_fault":"")+".json",
                    model(textures,deviceParts(i)));
        }
        Map<String,Object> variants=new LinkedHashMap<>();
        for(int d=0;d<4;d++)for(boolean fault:new boolean[]{false,true})
            variants.put("facing="+DIRS[d]+",faulted="+fault,
                    Map.of("model",modelId(id+(fault?"_fault":"")),"y",d*90));
        json("assets/venusmod/blockstates/"+id+".json",Map.of("variants",variants));
        json("assets/venusmod/models/item/"+id+".json",Map.of("parent",modelId(id)));loot(id);
    }
    private static void module(int i)throws IOException{
        String id=MODULES[i];language("item",id,MODULE_NAMES[i]);
        JA.put("tooltip.venusmod.network."+id,MODULE_JA[i]);
        EN.put("tooltip.venusmod.network."+id,MODULE_EN[i]);
        var img=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);var g=img.createGraphics();
        int base=i>=6&&i<=8?0x4a8560:i>=11&&i<=15?0x725797:0x46657f;
        g.setColor(new Color(0x16212c));g.fillRect(2,2,12,11);g.setColor(new Color(base));g.fillRect(3,3,10,9);g.setColor(new Color(0xe5bd58));g.drawRect(3,3,9,8);for(int x=4;x<13;x+=2)g.fillRect(x,12,1,3);
        g.setColor(new Color(0x222c38));g.fillRect(6,5,5,4);g.setColor(new Color(0xf1d780));for(int y=0;y<3;y++)if(((i+1)>>y&1)!=0)g.fillRect(4,5+y*2,1,1);
        if(i==8){g.setColor(new Color(0xb9f1ef));g.drawLine(5,7,7,9);g.drawLine(7,9,11,5);}if(i==16){g.setColor(new Color(0xdc655a));g.fillRect(7,4,2,7);g.fillRect(5,6,6,2);}if(i==1){g.setColor(new Color(0xe4bd48));g.fillRect(5,5,7,6);g.setColor(new Color(0x8e6b2c));g.drawLine(5,8,11,8);}
        if(i==9){g.setColor(new Color(0xdda064));g.drawRect(5,4,6,6);}
        if(i==10){g.setColor(new Color(0x19352a));g.fillRect(4,6,8,4);g.setColor(new Color(0x9ccbb0));for(int x=5;x<12;x+=3)g.fillRect(x,7,2,2);}
        if(i==17){g.setColor(new Color(0xcbdae5));g.fillRect(7,5,2,7);g.fillRect(5,4,6,3);g.setColor(new Color(0x243442));g.fillRect(7,3,2,3);}
        g.dispose();png("item/network/"+id,img);
        json("assets/venusmod/models/item/"+id+".json",Map.of("parent","minecraft:item/generated","textures",Map.of("layer0","venusmod:item/network/"+id)));
    }
    private static void loot(String id)throws IOException{json("data/venusmod/loot_table/blocks/"+id+".json",Map.of("type","minecraft:block","pools",List.of(Map.of("rolls",1,"entries",List.of(Map.of("type","minecraft:item","name","venusmod:"+id)),"conditions",List.of(Map.of("condition","minecraft:survives_explosion"))))));}
    private static String own(String name){return "venusmod:"+name;}
    private static void shaped(String id,int count,List<String> pattern,Map<String,String> key)throws IOException{
        Map<String,Object> ingredients=new LinkedHashMap<>();key.forEach((k,v)->ingredients.put(k,Map.of("item",v)));
        json("data/venusmod/recipe/"+id+".json",Map.of("type","minecraft:crafting_shaped","category","misc","pattern",pattern,"key",ingredients,"result",Map.of("id",own(id),"count",count)));
    }
    private static void recipes()throws IOException{
        shaped("network_gold_circuit",1,List.of("GRG","RQR","GRG"),Map.of("G","minecraft:gold_ingot","R","minecraft:redstone","Q","minecraft:quartz"));
        shaped("network_gold_bundle",1,List.of("GGG","G G","GGG"),Map.of("G","minecraft:gold_block"));
        for(int i=0;i<CABLES.length;i++){
            String material=switch(i){case 0->"minecraft:copper_ingot";case 1,2->"minecraft:gold_ingot";case 3,5->"minecraft:gold_block";case 6->"minecraft:diamond";case 7->own("venesite");default->"minecraft:glass";};
            String upgrade=i==0?"minecraft:string":i==4?own("network_gold_circuit"):own(CABLES[i-1]);
            shaped(CABLES[i],i==0?16:i<3?8:i<5?4:2,List.of("MMM","UUU","MMM"),Map.of("M",material,"U",upgrade));
            List<String> palette=new ArrayList<>();
            palette.add(own(CABLES[i]));
            for(String dye:DYE_IDS){
                String variant=CABLES[i]+"_"+dye;
                palette.add(own(variant));
                json("data/venusmod/recipe/"+variant+".json",Map.of(
                    "type","minecraft:crafting_shapeless","category","misc",
                    "ingredients",List.of(Map.of("tag",own("network_cables/"+CABLES[i])),Map.of("item","minecraft:"+dye+"_dye")),
                    "result",Map.of("id",own(variant),"count",1)));
            }
            json("data/venusmod/tags/item/network_cables/"+CABLES[i]+".json",Map.of("replace",false,"values",palette));
        }
        for(int i=0;i<DEVICES.length;i++)shaped(DEVICES[i],1,List.of("IGI","CBC","IGI"),Map.of("I","minecraft:iron_ingot","G","minecraft:gold_ingot","C",own("network_gold_circuit"),"B",switch(i){case 0,6->"minecraft:glass";case 4,5->"minecraft:amethyst_shard";case 7->"minecraft:repeater";default->"minecraft:quartz";}));
        String[] port={"network_port_10g","network_port_40g","network_port_100g","network_port_400g"};
        for(int i=0;i<4;i++)shaped(port[i],1,List.of("GPG","APA","GPG"),Map.of("G",i==0?"minecraft:gold_block":own("network_gold_bundle"),"P",i==0?own("network_gold_circuit"):own(port[i-1]),"A",switch(i){case 0->"minecraft:diamond";case 1->"minecraft:netherite_scrap";case 2->"minecraft:netherite_ingot";default->"minecraft:nether_star";}));
        shaped("network_stability_50",1,List.of("GIG","ICI","GIG"),Map.of("G","minecraft:gold_ingot","I","minecraft:iron_ingot","C",own("network_gold_circuit")));
        shaped("network_stability_90",1,List.of("GSG","DSD","GSG"),Map.of("G","minecraft:gold_block","S",own("network_stability_50"),"D","minecraft:diamond"));
        shaped("network_stability_perfect",1,List.of("NSN","VXV","NSN"),Map.of("N","minecraft:netherite_ingot","S",own("network_stability_90"),"V",own("venesite"),"X","minecraft:nether_star"));
        shaped("network_processor",1,List.of("GDG","NCN","GDG"),Map.of("G","minecraft:gold_block","D","minecraft:diamond","N",own("nickel_ingot"),"C",own("network_gold_circuit")));
        shaped("network_buffer",1,List.of("GCG","RCR","GCG"),Map.of("G","minecraft:gold_ingot","C",own("network_gold_circuit"),"R","minecraft:redstone_block"));
        for(int i=1;i<=5;i++){
            String previous=i==1?own("network_gold_circuit"):own("network_wan_"+(i-1));
            shaped("network_wan_"+i,1,List.of("GPG","PXP","GPG"),Map.of("G",i<3?"minecraft:gold_block":own("network_gold_bundle"),"P",previous,"X",switch(i){case 1->"minecraft:quartz";case 2->"minecraft:diamond_block";case 3->"minecraft:netherite_ingot";default->"minecraft:nether_star";}));
        }
        shaped("network_repair_kit",4,List.of("IGI","RCR"),Map.of("I","minecraft:iron_ingot","G","minecraft:gold_ingot","R","minecraft:redstone","C",own("network_gold_circuit")));
        shaped("network_configurator",1,List.of(" G ","ICI"," R "),Map.of("G","minecraft:gold_ingot","I","minecraft:iron_ingot","C",own("network_gold_circuit"),"R","minecraft:redstone"));
    }
    private static void png(String name,BufferedImage image)throws IOException{Path p=out.resolve("assets/venusmod/textures/"+name+".png");Files.createDirectories(p.getParent());ImageIO.write(image,"png",p.toFile());}
    private static void json(String name,Object value)throws IOException{Path p=out.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,encode(value)+"\n",StandardCharsets.UTF_8);}
    private static String encode(Object v){
        if(v instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
        if(v instanceof Map<?,?> m){List<String> a=new ArrayList<>();m.keySet().stream().map(Object::toString).sorted().forEach(k->a.add(encode(k)+":"+encode(m.get(k))));return "{"+String.join(",",a)+"}";}
        if(v instanceof Collection<?> c)return "["+String.join(",",c.stream().map(NetworkAssetGenerator::encode).toList())+"]";
        if(v instanceof double[] a){List<String> x=new ArrayList<>();for(double n:a)x.add(Double.toString(n));return "["+String.join(",",x)+"]";}
        return String.valueOf(v);
    }
}
