package dev.ssscfw.venusmod.infrastructure.network.core;

/** IPv4[:port]だけを受け付ける。URI/外部DNS/ソケットAPIに渡さない。 */
public record ServiceAddress(String address,int port) {
    public ServiceAddress {
        address=Ipv4.normalize(address);
        if(port<1||port>65535)throw new IllegalArgumentException("サービスのポート番号は1〜65535です");
    }
    public static ServiceAddress parse(String text,int defaultPort){
        if(text==null||text.length()>21||text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("IPv4またはIPv4:ポートで入力してください");
        String[] parts=text.split(":",-1);
        if(parts.length<1||parts.length>2||parts.length==2&&!parts[1].matches("[0-9]{1,5}"))
            throw new IllegalArgumentException("IPv4またはIPv4:ポートで入力してください");
        return new ServiceAddress(parts[0],parts.length==2?Integer.parseInt(parts[1]):defaultPort);
    }
}
