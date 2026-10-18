package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.Locale;

/** 既存仮想サービスの応答確認。任意TCPポートのopen判定やサービス操作はしない。 */
public record MonitorTarget(Kind kind,String address,int port) {
    public enum Kind {
        ICMP("icmp",0), WEB("tcp",NetworkSimulator.WEB_PORT), DNS("udp",53),
        TRANSFER("tcp",NetworkSimulator.TRANSFER_PORT), CONTROL("tcp",NetworkSimulator.CONTROL_PORT);
        private final String protocol;
        private final int servicePort;
        Kind(String protocol,int servicePort){this.protocol=protocol;this.servicePort=servicePort;}
        public String protocol(){return protocol;}
        public int servicePort(){return servicePort;}
        public String label(){return name().toLowerCase(Locale.ROOT);}
        public static Kind parse(String value){
            try{return valueOf(value.toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException invalid){throw new IllegalArgumentException("監視種類はicmp/web/dns/transfer/controlです");}
        }
        /** ここでデータ取得・出力変更は行わず、到達後の稼働条件だけを検査する。 */
        public String unavailable(NetworkProfile target){
            if(target==null||target.failed)return "監視対象が停止しています";
            return switch(this){
                case ICMP,TRANSFER->"";
                case WEB->target.kind==DeviceKind.SERVER&&target.web?"":"Webサービスが停止または非対応です";
                case DNS->DnsSettings.available(target)?"":"DNSサービスが停止または非対応です";
                case CONTROL->target.kind==DeviceKind.REDSTONE_TERMINAL?"":"レッドストーン制御サービスが非対応です";
            };
        }
    }
    /** IP一覧は従来形式で保存し、種類と公開ポートをv11の追加節で関連付ける。 */
    public record Service(Kind kind,int port) {
        public Service {
            if(kind==null||kind==Kind.ICMP||port<1||port>65535)
                throw new IllegalArgumentException("サービス監視の種類/公開ポートが不正です");
        }
    }
    public MonitorTarget {
        if(kind==null)throw new IllegalArgumentException("監視種類がありません");
        address=MonitorSettings.address(address);
        if(kind==Kind.ICMP?port!=0:port<1||port>65535)throw new IllegalArgumentException("監視ポートが不正です");
    }
    public static MonitorTarget parse(String type,String endpoint){
        Kind kind=Kind.parse(type);
        if(kind==Kind.ICMP)return new MonitorTarget(kind,endpoint,0);
        var parsed=ServiceAddress.parse(endpoint,kind.servicePort());
        return new MonitorTarget(kind,parsed.address(),parsed.port());
    }
    public String description(){return kind==Kind.ICMP?address:address+":"+port+" ["+kind.label()+"]";}
}
