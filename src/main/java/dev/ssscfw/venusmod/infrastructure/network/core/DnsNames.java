package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.Locale;

/** ゲーム内の名前専用。java.netや実DNSへ委譲しない。 */
public final class DnsNames {
    public static final int MAX_NAME=64;
    private DnsNames(){}
    public static String name(String value){
        if(value==null||value.length()>MAX_NAME+1)throw new IllegalArgumentException("名前は64文字までです");
        String name=legacyKey(value);
        if(name.isEmpty()||name.length()>MAX_NAME||numeric(name)||!Character.toString(name.charAt(0)).matches("[a-z0-9]"))
            throw new IllegalArgumentException("ゲーム内ホスト名が不正です");
        for(String label:name.split("\\.",-1))if(!label.matches("[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?"))
            throw new IllegalArgumentException("名前の各区切りは1〜63文字の英数字・ハイフン・下線です");
        return name;
    }
    static String legacyKey(String value){return (value.endsWith(".")?value.substring(0,value.length()-1):value).toLowerCase(Locale.ROOT);}
    public static boolean numeric(String value){return value!=null&&value.matches("[0-9.]+");}
    /** 数値らしい壊れたIPは名前として再解釈しない。 */
    public static String target(String value){return numeric(value)?Ipv4.normalize(value):name(value);}
    public record Endpoint(String host,int port){
        public Endpoint{host=target(host);if(port<1||port>65535)throw new IllegalArgumentException("サービスのポートは1〜65535です");}
    }
    public static Endpoint endpoint(String text,int defaultPort){
        if(text==null||text.length()>MAX_NAME+7||text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("名前またはIPv4[:ポート]で指定してください");
        String[] parts=text.split(":",-1);
        if(parts.length>2||parts.length==2&&!parts[1].matches("[0-9]{1,5}"))throw new IllegalArgumentException("名前またはIPv4[:ポート]で指定してください");
        return new Endpoint(parts[0],parts.length==2?Integer.parseInt(parts[1]):defaultPort);
    }
}
