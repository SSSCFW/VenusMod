package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** 保存するのは監視方針のみ。到達状態・連続回数・時計は稼働時に再作成する。 */
public final class MonitorSettings {
    public static final int MAX_TARGETS=16;
    public boolean enabled;
    public int intervalSeconds=5,failures=3,recoveries=2;
    public final Map<String,String> targets=new LinkedHashMap<>();
    public final Map<String,MonitorTarget.Service> services=new LinkedHashMap<>();
    public record Config(boolean enabled,int interval,int failures,int recoveries,Map<String,String> targets,Map<String,MonitorTarget.Service> services){
        public Config {targets=Collections.unmodifiableMap(new LinkedHashMap<>(targets));services=Collections.unmodifiableMap(new LinkedHashMap<>(services));}
        public MonitorTarget target(String label){var service=services.get(label);return new MonitorTarget(service==null?MonitorTarget.Kind.ICMP:service.kind(),targets.get(label),service==null?0:service.port());}
    }
    public Config config(){return new Config(enabled,intervalSeconds,failures,recoveries,targets,services);}
    public static void label(String label){if(label==null||!label.matches("[a-zA-Z0-9_-]{1,24}"))throw new IllegalArgumentException("監視名は英数字・_・-の1〜24文字です");}
    public static String address(String text){String ip=Ipv4.normalize(text);long n=Ipv4.parse(ip);if(n==0||n>=0xe0000000L)throw new IllegalArgumentException("監視先はユニキャストIPv4です");return ip;}
    public void validate(NetworkProfile owner){
        if(intervalSeconds<1||intervalSeconds>3600||failures<1||failures>10||recoveries<1||recoveries>10)throw new IllegalArgumentException("監視間隔1〜3600秒、失敗/復旧閾値1〜10回です");
        if(targets.size()>MAX_TARGETS)throw new IllegalArgumentException("監視対象は1台16件までです");
        if((enabled||!targets.isEmpty())&&owner.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("定期監視はネットワークサーバーのみです");
        if(enabled&&targets.isEmpty())throw new IllegalArgumentException("先にmonitor add NAME IPで対象を登録してください");
        for(var e:targets.entrySet()){label(e.getKey());address(e.getValue());}
        if(services.size()>MAX_TARGETS)throw new IllegalArgumentException("監視種類の件数上限です");
        for(var e:services.entrySet())if(!targets.containsKey(e.getKey())||e.getValue()==null)throw new IllegalArgumentException("対象のない監視種類です");
    }
    public void copyFrom(MonitorSettings other){enabled=other.enabled;intervalSeconds=other.intervalSeconds;failures=other.failures;recoveries=other.recoveries;targets.clear();targets.putAll(other.targets);services.clear();services.putAll(other.services);}
    public void write(DataOutput out)throws IOException{out.writeBoolean(enabled);out.writeInt(intervalSeconds);out.writeInt(failures);out.writeInt(recoveries);out.writeInt(targets.size());for(var e:targets.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}}
    public void read(DataInput in)throws IOException{enabled=in.readBoolean();intervalSeconds=in.readInt();failures=in.readInt();recoveries=in.readInt();int n=in.readInt();if(n<0||n>MAX_TARGETS)throw new IllegalArgumentException("監視対象件数上限");targets.clear();for(int i=0;i<n;i++){String name=in.readUTF(),ip=in.readUTF();if(targets.putIfAbsent(name,ip)!=null)throw new IllegalArgumentException("監視名が重複しています");}}
    public void writeServices(DataOutput out)throws IOException{
        out.writeInt(services.size());for(var e:services.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue().kind().name());out.writeInt(e.getValue().port());}
    }
    public void readServices(DataInput in)throws IOException{
        int n=in.readInt();if(n<0||n>MAX_TARGETS)throw new IllegalArgumentException("監視種類件数の上限です");
        services.clear();for(int i=0;i<n;i++){String label=in.readUTF();var service=new MonitorTarget.Service(MonitorTarget.Kind.valueOf(in.readUTF()),in.readInt());
            if(!targets.containsKey(label)||services.putIfAbsent(label,service)!=null)throw new IllegalArgumentException("孤立/重複した監視種類です");}
    }

}
