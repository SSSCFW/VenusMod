package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.Locale;

/** ACL画面の下書き。確認した規則の全内容だけを単回の削除命令へ変換する。 */
public final class AclDraft {
    public static final int MAX_FIELD_TEXT=64;
    private int port;
    private NetworkPolicy.Direction direction=NetworkPolicy.Direction.IN;
    private boolean permit=true;
    private NetworkPolicy.Protocol protocol=NetworkPolicy.Protocol.IP;
    private String sequence="10",source="any",destination="any",destinationPort="any";
    private NetworkPolicy.Rule pending;

    public static boolean supported(DeviceKind kind){return kind!=null&&kind.routing();}
    public int port(){return port;}
    public NetworkPolicy.Direction direction(){return direction;}
    public boolean permit(){return permit;}
    public NetworkPolicy.Protocol protocol(){return protocol;}
    public String sequenceText(){return sequence;}
    public String sourceText(){return source;}
    public String destinationText(){return destination;}
    public String destinationPortText(){return destinationPort;}
    public void port(int value){dismissDeletion();if(value<0||value>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("機器ポートは0〜5です");port=value;}
    public void direction(NetworkPolicy.Direction value){dismissDeletion();if(value==null)throw new IllegalArgumentException("方向を選択してください");direction=value;}
    public void permit(boolean value){dismissDeletion();permit=value;}
    public void protocol(NetworkPolicy.Protocol value){dismissDeletion();if(value==null)throw new IllegalArgumentException("プロトコルを選択してください");protocol=value;}
    public void sequenceText(String value){dismissDeletion();sequence=field(value);}
    public void sourceText(String value){dismissDeletion();source=field(value);}
    public void destinationText(String value){dismissDeletion();destination=field(value);}
    public void destinationPortText(String value){dismissDeletion();destinationPort=field(value);}

    public String addCommand(){dismissDeletion();return command("add",rule());}
    public void requestDeletion(){dismissDeletion();pending=rule();}
    public boolean deletionPending(){return pending!=null;}
    public String pendingDescription(){return pending==null?"":pending.description()+(pending.destinationPort()<0?" dst-port=any":"");}
    public String confirmDeletion(){
        var target=pending;dismissDeletion();
        if(target==null)throw new IllegalArgumentException("削除するACL規則を再確認してください");
        return command("delete-match",target);
    }
    public void dismissDeletion(){pending=null;}

    private NetworkPolicy.Rule rule(){
        String service=token(destinationPort);
        int servicePort=service.equalsIgnoreCase("any")?-1:number(service,0,65535,"宛先ポート");
        return new NetworkPolicy.Rule(port,direction,number(token(sequence),1,65535,"順序番号"),permit,protocol,prefix(source),prefix(destination),servicePort);
    }
    private static NetworkPolicy.Prefix prefix(String text){
        String value=token(text);
        if(!value.equalsIgnoreCase("any")&&!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}(/[0-9]{1,2})?"))
            throw new IllegalArgumentException("IPv4、CIDR（/0〜/32）、anyを指定してください");
        return NetworkPolicy.Prefix.parse(value);
    }
    private static int number(String value,int min,int max,String label){
        if(!value.matches("[0-9]{1,5}"))throw new IllegalArgumentException(label+"は"+min+"〜"+max+"の整数です");
        int result=Integer.parseInt(value);if(result<min||result>max)throw new IllegalArgumentException(label+"は"+min+"〜"+max+"です");return result;
    }
    private static String field(String text){if(text==null||text.length()>MAX_FIELD_TEXT)throw new IllegalArgumentException("入力は64文字以内です");return text;}
    private static String token(String text){
        field(text);if(text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("改行・制御文字は入力できません");
        String value=text.strip();if(value.isEmpty())throw new IllegalArgumentException("空欄にはanyまたは値を指定してください");return value;
    }
    private static String command(String verb,NetworkPolicy.Rule rule){
        return "acl "+verb+" "+rule.port()+" "+rule.direction().name().toLowerCase(Locale.ROOT)+" "+rule.sequence()+" "+(rule.permit()?"permit":"deny")
                +" "+rule.protocol().name().toLowerCase(Locale.ROOT)+" "+rule.source()+" "+rule.destination()+" "+(rule.destinationPort()<0?"any":rule.destinationPort());
    }
}
