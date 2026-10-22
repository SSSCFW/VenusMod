package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** IPv4のステートレスACL。ARPや現実のソケットを対象にしない。 */
public final class NetworkPolicy {
    public static final int MAX_RULES=64;
    public enum Direction { IN, OUT }
    public enum Protocol { IP, ICMP, TCP, UDP, OSPF, VRRP }
    public record Prefix(long network,int length) {
        public Prefix { network &= Ipv4.mask(length); }
        public static Prefix parse(String text) {
            if("any".equalsIgnoreCase(text))return new Prefix(0,0);
            String[] parts=text.split("/",-1);
            if(parts.length>2||parts.length==0)throw new IllegalArgumentException("CIDR または any を指定してください");
            return new Prefix(Ipv4.parse(parts[0]),parts.length==1?32:Integer.parseInt(parts[1]));
        }
        public boolean contains(long address){return (address&Ipv4.mask(length))==network;}
        @Override public String toString(){return Ipv4.format(network)+"/"+length;}
    }
    public record Packet(Protocol protocol,long source,long destination,int sourcePort,int destinationPort) {
        public Packet {
            Objects.requireNonNull(protocol);
            if(source<0||source>0xffffffffL||destination<0||destination>0xffffffffL)throw new IllegalArgumentException("IPv4範囲外です");
            if(sourcePort<0||sourcePort>65535||destinationPort<0||destinationPort>65535)throw new IllegalArgumentException("通信ポートは0〜65535です");
        }
        public static Packet of(String protocol,String source,String destination,int sourcePort,int destinationPort) {
            return new Packet(Protocol.valueOf(protocol.toUpperCase(Locale.ROOT)),Ipv4.parse(source),Ipv4.parse(destination),sourcePort,destinationPort);
        }
        public Packet reverse(){return new Packet(protocol,destination,source,destinationPort,sourcePort);}
    }
    public record Rule(int port,Direction direction,int sequence,boolean permit,Protocol protocol,Prefix source,Prefix destination,int destinationPort) {
        public Rule {
            Objects.requireNonNull(direction);Objects.requireNonNull(protocol);Objects.requireNonNull(source);Objects.requireNonNull(destination);
            if(port<0||port>=NetworkProfile.PORT_COUNT||sequence<1||sequence>65535)throw new IllegalArgumentException("機器ポートは0〜5、順序番号は1〜65535です");
            if(destinationPort< -1||destinationPort>65535||destinationPort>=0&&protocol!=Protocol.TCP&&protocol!=Protocol.UDP)
                throw new IllegalArgumentException("宛先ポート指定はTCP/UDPで0〜65535またはanyです");
        }
        public boolean matches(Packet packet){return (protocol==Protocol.IP||protocol==packet.protocol)&&source.contains(packet.source)&&destination.contains(packet.destination)&&(destinationPort<0||destinationPort==packet.destinationPort);}
        public String description(){return "P"+port+" "+direction.name().toLowerCase(Locale.ROOT)+" #"+sequence+" "+(permit?"permit":"deny")+" "+protocol.name().toLowerCase(Locale.ROOT)+" "+source+" → "+destination+(destinationPort<0?"":" dst-port="+destinationPort);}
    }
    public record Verdict(boolean allowed,int sequence,int checked) {}
    private final List<Rule> rules=new ArrayList<>();
    public List<Rule> rules(){return List.copyOf(rules);}
    public void add(Rule rule){
        if(rules.size()>=MAX_RULES)throw new IllegalArgumentException("ACLは機器全体で64規則までです");
        if(rules.stream().anyMatch(r->r.port==rule.port&&r.direction==rule.direction&&r.sequence==rule.sequence))throw new IllegalArgumentException("同じポート・方向・順序番号の規則があります");
        rules.add(rule);rules.sort(Comparator.comparingInt(Rule::port).thenComparing(Rule::direction).thenComparingInt(Rule::sequence));
    }
    public void remove(int port,Direction direction,int sequence){if(!rules.removeIf(r->r.port==port&&r.direction==direction&&r.sequence==sequence))throw new IllegalArgumentException("指定規則はありません");}
    /** 確認した全内容を照合し、同じキーへ差し替えられた規則を削除しない。 */
    public void removeMatching(Rule expected){
        if(!rules.remove(expected))throw new IllegalArgumentException("確認したACL規則がないか内容が変わりました。一覧で現在値を確認してください");
    }
    public void clear(int port,Direction direction){rules.removeIf(r->r.port==port&&r.direction==direction);}
    /** 規則がない方向は未適用。1件以上ある方向は最初の一致を採用、未一致を拒否。 */
    public Verdict decide(int port,Direction direction,Packet packet){
        boolean attached=false;int checked=0;
        for(Rule r:rules){checked++;if(r.port!=port||r.direction!=direction)continue;attached=true;if(r.matches(packet))return new Verdict(r.permit,r.sequence,checked);}
        return new Verdict(!attached,0,checked);
    }
    public void write(DataOutput out)throws IOException {
        out.writeInt(rules.size());for(Rule r:rules){out.writeByte(r.port);out.writeUTF(r.direction.name());out.writeInt(r.sequence);out.writeBoolean(r.permit);out.writeUTF(r.protocol.name());out.writeUTF(r.source.toString());out.writeUTF(r.destination.toString());out.writeInt(r.destinationPort);}
    }
    public void read(DataInput in)throws IOException {
        int count=in.readInt();if(count<0||count>MAX_RULES)throw new IllegalArgumentException("ACL保存件数の上限です");
        for(int i=0;i<count;i++)add(new Rule(in.readUnsignedByte(),Direction.valueOf(in.readUTF()),in.readInt(),in.readBoolean(),Protocol.valueOf(in.readUTF()),Prefix.parse(in.readUTF()),Prefix.parse(in.readUTF()),in.readInt()));
    }
}
