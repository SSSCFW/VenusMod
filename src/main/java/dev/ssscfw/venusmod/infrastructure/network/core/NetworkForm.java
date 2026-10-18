package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/**
 * GUIの公開編集フィールド。ACL・認証・所有者・モジュール実体を送信しない。
 * クライアント値をProfileとして復元せず、最新設定のコピーへ許可フィールドだけ反映する。
 */
public final class NetworkForm {
    public static final int MAX_ENCODED=1024, BASIC=-1, PAGE_COUNT=NetworkProfile.PORT_COUNT+1;
    private static final int VIEW_MAGIC=0x564e4631, EDIT_MAGIC=0x564e4531;
    public static final List<Long> SPEEDS=List.of(0L,10_000_000L,100_000_000L,1_000_000_000L,
            10_000_000_000L,40_000_000_000L,100_000_000_000L,400_000_000_000L);
    private NetworkForm(){}

    /** 文字入力は検証前なので数値も文字列。scope=-1は基本設定、0〜5は世界方位のポート。 */
    public record Values(int scope,String name,String address,String prefix,String gateway,String dns,
                         boolean enabled,boolean trunk,boolean optical,String vlan,String speed){}
    public record View(DeviceKind kind,Values values,long maximumBps,boolean opticalAvailable){}
    public record Outcome(boolean accepted,boolean changed,String message){
        public static Outcome reject(String reason){return new Outcome(false,false,reason);}
    }

    public static View capture(NetworkProfile p,int scope){
        scope(scope);
        if(scope==BASIC)return new View(p.kind,new Values(scope,p.name,p.address,Integer.toString(p.prefix),p.gateway,p.dns,true,false,false,"1","0"),0,false);
        var port=p.ports[scope];
        return new View(p.kind,new Values(scope,"",port.address,Integer.toString(port.prefix),"","",port.enabled,port.trunk,port.optical,
                Integer.toString(port.vlan),Long.toString(port.speedLimitBps)),
                NetworkProfile.SPEEDS[Math.clamp(port.moduleTier,0,4)],port.moduleTier>0||p.kind.optical()&&scope==0);
    }
    public static List<String> captureAll(NetworkProfile p){
        var result=new ArrayList<String>(PAGE_COUNT);for(int scope=BASIC;scope<NetworkProfile.PORT_COUNT;scope++)result.add(encode(capture(p,scope)));return List.copyOf(result);
    }

    /** 現在値比較により同じ範囲の同時編集を拒否。別ポート・ACL等への編集は保持する。 */
    public static Outcome apply(NetworkSimulator simulator,String id,String expected,String edit){
        try{
            NetworkProfile current=simulator.nodes().get(id);if(current==null)return Outcome.reject("機器が未登録です");
            View base=decodeView(expected);Values fields=decodeValues(edit);scope(fields.scope());
            if(base.kind()!=current.kind||fields.scope()!=base.values().scope())return Outcome.reject("機器・編集範囲が一致しません");
            if(!capture(current,fields.scope()).equals(base))return Outcome.reject("ほかの操作で設定・装着品が変わりました。「再読込」で現在値を確認してください");
            var p=NetworkProfile.decode(current.kind,current.encode());
            p.stability=current.stability;p.processor=current.processor;p.buffer=current.buffer;p.wanTier=current.wanTier;
            for(int i=0;i<NetworkProfile.PORT_COUNT;i++)p.ports[i].moduleTier=current.ports[i].moduleTier;
            boolean connectivity;
            if(fields.scope()==BASIC){
                if(fields.enabled()!=base.values().enabled()||fields.trunk()!=base.values().trunk()||fields.optical()!=base.values().optical()
                        ||!fields.vlan().equals(base.values().vlan())||!fields.speed().equals(base.values().speed()))
                    return Outcome.reject("基本設定からポートを変更できません");
                text(fields.name(),48,"機器名");p.name=fields.name();
                if(p.kind.routing()){
                    if(!fields.address().equals(base.values().address())||!fields.prefix().equals(base.values().prefix())||!fields.gateway().equals(base.values().gateway()))
                        return Outcome.reject("L3機器のIPは各ポートで、経路はCLIで設定してください");
                }else{
                    p.address=ip(fields.address());p.prefix=number(fields.prefix(),0,32,"プレフィックス");p.gateway=ip(fields.gateway());
                }
                p.dns=ip(fields.dns());
                connectivity=!p.address.equals(current.address)||p.prefix!=current.prefix||!p.gateway.equals(current.gateway);
            }else{
                if(!fields.name().isEmpty()||!fields.gateway().isEmpty()||!fields.dns().isEmpty())return Outcome.reject("ポートから機器全体の設定は変更できません");
                int n=fields.scope();var port=p.ports[n];var previous=current.ports[n];
                if(!p.kind.routing()){
                    if(!fields.address().equals(base.values().address())||!fields.prefix().equals(base.values().prefix()))return Outcome.reject("この機種のIPは基本設定で変更してください");
                }else{port.address=ip(fields.address());port.prefix=number(fields.prefix(),0,32,"プレフィックス");}
                port.vlan=number(fields.vlan(),1,4094,"VLAN");
                text(fields.speed(),16,"速度");if(!fields.speed().matches("[0-9]{1,12}"))return Outcome.reject("速度の形式が不正です");
                long speed=Long.parseLong(fields.speed());
                // 既存の高い固定速度は、モジュール抜去で勝手に書き換えない。他フィールドの編集は可能。
                if(speed!=previous.speedLimitBps&&(!SPEEDS.contains(speed)||speed>base.maximumBps()))return Outcome.reject("対応するポートモジュールの速度を選択してください");
                if(fields.optical()&&!previous.optical&&!base.opticalAvailable())return Outcome.reject("光への変更には対応モジュールが必要です");
                port.speedLimitBps=speed;port.enabled=fields.enabled();port.trunk=fields.trunk();port.optical=fields.optical();connectivity=true;
            }
            p.validate();p.encode();
            if(capture(p,fields.scope()).equals(base))return new Outcome(true,false,"変更はありません");
            if(connectivity)simulator.put(id,p);
            else{current.name=p.name;current.dns=p.dns;simulator.dnsResolver().synchronizeClient(id,current);}
            // 永続化はOutcome.changedを受け取る実メニューが対象BlockEntityに行う。
            return new Outcome(true,true,"設定を保存しました"+(connectivity?"。影響する通信は再確立します":""));
        }catch(IOException|IllegalArgumentException e){return Outcome.reject("設定を変更しませんでした: "+e.getMessage());}
    }

    public static String encode(View view){return write(out->{out.writeInt(VIEW_MAGIC);out.writeByte(view.kind().ordinal());writeValues(out,view.values());out.writeLong(view.maximumBps());out.writeBoolean(view.opticalAvailable());});}
    public static String encode(Values values){return write(out->{out.writeInt(EDIT_MAGIC);writeValues(out,values);});}
    public static View decodeView(String encoded)throws IOException{
        try(var in=input(encoded)){
            if(in.readInt()!=VIEW_MAGIC)throw new IllegalArgumentException("表示データ形式が不正です");int kind=in.readUnsignedByte();if(kind>=DeviceKind.values().length)throw new IllegalArgumentException("機器種別が不正です");
            Values fields=readValues(in);long maximum=in.readLong();boolean optical=in.readBoolean();end(in);
            if(maximum<0||maximum>NetworkProfile.SPEEDS[4])throw new IllegalArgumentException("表示速度が不正です");
            return new View(DeviceKind.values()[kind],fields,maximum,optical);
        }
    }
    public static Values decodeValues(String encoded)throws IOException{
        try(var in=input(encoded)){if(in.readInt()!=EDIT_MAGIC)throw new IllegalArgumentException("入力データ形式が不正です");Values values=readValues(in);end(in);return values;}
    }
    private static void scope(int n){if(n<BASIC||n>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("編集範囲は基本設定またはP0〜P5です");}
    private static String ip(String value){text(value,64,"IPv4");return value.isBlank()?"":Ipv4.normalize(value.strip());}
    private static int number(String value,int min,int max,String field){text(value,8,field);if(!value.matches("[0-9]{1,4}"))throw new IllegalArgumentException(field+"は整数で入力してください");int n=Integer.parseInt(value);if(n<min||n>max)throw new IllegalArgumentException(field+"は"+min+"〜"+max+"です");return n;}
    private static void text(String value,int max,String field){if(value==null||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException(field+"の文字数または形式が不正です");}
    private static void writeValues(DataOutput out,Values v)throws IOException{
        out.writeByte(v.scope());out.writeUTF(v.name());out.writeUTF(v.address());out.writeUTF(v.prefix());out.writeUTF(v.gateway());out.writeUTF(v.dns());
        out.writeBoolean(v.enabled());out.writeBoolean(v.trunk());out.writeBoolean(v.optical());out.writeUTF(v.vlan());out.writeUTF(v.speed());
    }
    private static Values readValues(DataInputStream in)throws IOException{
        int scope=in.readByte();scope(scope);
        String name=readText(in),address=readText(in),prefix=readText(in),gateway=readText(in),dns=readText(in);
        boolean enabled=in.readBoolean(),trunk=in.readBoolean(),optical=in.readBoolean();
        return new Values(scope,name,address,prefix,gateway,dns,enabled,trunk,optical,readText(in),readText(in));
    }
    private static String readText(DataInputStream in)throws IOException{String s=in.readUTF();if(s.length()>64)throw new IllegalArgumentException("入力欄は64文字以下です");return s;}
    private static DataInputStream input(String encoded){if(encoded==null||encoded.length()>MAX_ENCODED)throw new IllegalArgumentException("フォーム容量の上限です");return new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));}
    private static void end(DataInputStream in)throws IOException{if(in.available()!=0)throw new IllegalArgumentException("余分なフォームデータです");}
    private interface Writer{void write(DataOutputStream out)throws IOException;}
    private static String write(Writer writer){
        try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){writer.write(out);}String result=Base64.getEncoder().encodeToString(bytes.toByteArray());if(result.length()>MAX_ENCODED)throw new IllegalArgumentException("フォーム容量の上限です");return result;}
        catch(IOException e){throw new IllegalStateException(e);}
    }
}
