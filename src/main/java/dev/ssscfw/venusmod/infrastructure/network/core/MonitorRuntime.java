package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 1対象1期限、全体最大256件。通常のサーバーtickだけで進み、過去分を一気に実行しない。 */
public final class MonitorRuntime {
    public static final int MAX_SERVERS=64,MAX_TARGETS=256,PROBES_PER_TICK=2;
    public record Probe(NetworkSimulator.Result result,boolean limited){}
    public enum State {UNKNOWN,UP,DOWN}
    public record Snapshot(String label,String address,State state,long checked,long changed,long probes,int successes,int failures,double rtt,String detail){}
    private static final class Entry {
        final String owner,label,address;
        State state=State.UNKNOWN;
        long due,order,checked=-1,changed=-1,probes;
        int successes,failures;
        double rtt=-1;
        String detail="未確認";
        Entry(String owner,String label,String address){this.owner=owner;this.label=label;this.address=address;}
        Snapshot snapshot(){return new Snapshot(label,address,state,checked,changed,probes,successes,failures,rtt,detail);}
    }
    private static final class Owner {
        final MonitorSettings.Config config;
        final List<Entry> entries=new ArrayList<>();
        boolean paused;
        Owner(MonitorSettings.Config config){this.config=config;}
    }
    private final NetworkSimulator simulator;
    private final Map<String,Owner> owners=new LinkedHashMap<>();
    private final Map<String,String> issues=new HashMap<>();
    private final PriorityQueue<Entry> due=new PriorityQueue<>(Comparator.comparingLong((Entry e)->e.due).thenComparingLong(e->e.order));
    private long now,order,totalProbes;
    private int lastProbes;
    public MonitorRuntime(NetworkSimulator simulator){this.simulator=simulator;}
    public long now(){return now;}
    public long totalProbes(){return totalProbes;}
    public int scheduled(){return due.size();}
    public int lastProbes(){return lastProbes;}
    public void checkCapacity(String id,MonitorSettings settings){
        if(!settings.enabled)return;
        int servers=owners.size()-(owners.containsKey(id)?1:0)+1;
        int targets=due.size()-(owners.containsKey(id)?owners.get(id).entries.size():0)+settings.targets.size();
        if(servers>MAX_SERVERS||targets>MAX_TARGETS)throw new IllegalArgumentException("監視全体上限: 64サーバー/256対象です。既存監視は維持します");
    }
    /** Profileが別の設定操作で差し替えられても、監視設定が同じなら時計と状態を保つ。 */
    public void synchronize(String id){
        var p=simulator.nodes().get(id);
        if(p==null||!p.monitor.enabled){remove(id);return;}
        var old=owners.get(id);var config=p.monitor.config();if(old!=null&&old.config.equals(config))return;
        try{p.monitor.validate(p);checkCapacity(id,p.monitor);}catch(IllegalArgumentException invalid){remove(id);issues.put(id,invalid.getMessage());return;}
        remove(id);var owner=new Owner(config);owner.paused=p.failed;owners.put(id,owner);
        for(var target:config.targets().entrySet()){var entry=new Entry(id,target.getKey(),target.getValue());owner.entries.add(entry);schedule(entry,1);}
    }
    public void remove(String id){Owner old=owners.remove(id);if(old!=null)due.removeAll(old.entries);issues.remove(id);}
    private static long plus(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    private void schedule(Entry entry,long ticks){entry.due=plus(now,ticks);entry.order=order++;due.add(entry);}
    public void advance(long ticks){
        if(ticks<=0)return;now=plus(now,ticks);lastProbes=0;
        // 期限切れ確認も最大2件。停止している機器群でも無制限に走査しない。
        for(int work=0;work<PROBES_PER_TICK&&!due.isEmpty()&&due.peek().due<=now;work++){
            Entry e=due.remove();Owner owner=owners.get(e.owner);var p=simulator.nodes().get(e.owner);
            if(owner==null||p==null)continue;
            if(p.failed){owner.paused=true;for(var member:owner.entries){member.successes=0;member.failures=0;}schedule(e,owner.config.interval()*20L);continue;}
            if(owner.paused){for(var member:owner.entries){member.successes=0;member.failures=0;}owner.paused=false;}
            var attempt=simulator.probeMonitor(e.owner,e.address);var result=attempt.result();lastProbes++;totalProbes++;e.probes++;e.checked=now;
            e.detail=SyslogRuntime.text(result.message(),160);e.rtt=result.success()?result.milliseconds():-1;
            if(attempt.limited()){e.successes=0;e.failures=0;e.detail="判定保留(処理上限): "+e.detail;schedule(e,owner.config.interval()*20L);continue;}
            State previous=e.state;
            if(result.success()){e.failures=0;e.successes=Math.min(owner.config.recoveries(),e.successes+1);if(e.successes>=owner.config.recoveries())e.state=State.UP;}
            else{e.successes=0;e.failures=Math.min(owner.config.failures(),e.failures+1);if(e.failures>=owner.config.failures())e.state=State.DOWN;}
            if(e.state!=previous){e.changed=now;simulator.log(e.owner,e.state==State.DOWN?3:5,"MONITOR "+e.state+" "+e.label+" "+e.address+" ("+previous+" → "+e.state+"): "+e.detail);}
            schedule(e,owner.config.interval()*20L);
        }
    }
    public List<Snapshot> snapshots(String id){Owner owner=owners.get(id);return owner==null?List.of():owner.entries.stream().map(Entry::snapshot).toList();}
    public List<String> describe(String id){
        var p=simulator.nodes().get(id);if(p==null)return List.of("監視元が未登録です");
        List<String> lines=new ArrayList<>();var config=p.monitor;Owner owner=owners.get(id);
        String mode=!config.enabled?"OFF":issues.containsKey(id)?"LIMIT":p.failed?"PAUSED":"ON";
        lines.add("Monitor "+mode+" / "+config.intervalSeconds+"秒 / 失敗"+config.failures+"回・復旧"+config.recoveries+"回 / 時刻"+now+"tick");
        if(issues.containsKey(id))lines.add(issues.get(id));
        lines.add("DOWNは観測地点からの応答なし。機器故障の断定ではありません。");
        if(owner==null){config.targets.forEach((label,address)->lines.add(label+" "+address+" UNKNOWN (未確認)"));}
        else for(Entry e:owner.entries){lines.add(e.label+" "+e.address+" "+e.state+" / 確認"+e.probes+"回 / 成功"+e.successes+" 失敗"+e.failures);
            lines.add("  最終="+(e.checked<0?"未確認":e.checked+"tick")+" / "+(e.rtt<0?"RTTなし":String.format(Locale.ROOT,"RTT %.2fms",e.rtt))+" / "+e.detail);}
        return List.copyOf(lines);
    }
}
