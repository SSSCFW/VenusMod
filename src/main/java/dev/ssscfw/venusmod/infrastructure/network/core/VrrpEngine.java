package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/**
 * RFC9568を参考にしたIPv4 VRRPの状態機械。1機器につき1グループ・非owner優先度。
 * 役割は受信した広告とローカル期限だけで決める。全体の優先度による選出はしない。
 */
public final class VrrpEngine {
    public static final int MAX_ROUTERS=128,MAX_PEERS=64,MAX_DELIVERIES=4096,MAX_EVENTS_PER_TICK=64;
    public enum State { INITIALIZE, BACKUP, MASTER }
    public record Configuration(int port,String address,int prefix,String vip,int group,int priority,int advertisementTicks,boolean preempt){
        public Configuration{
            address=Ipv4.normalize(address);vip=Ipv4.normalize(vip);
            if(port<0||port>=6||prefix<1||prefix>30||group<1||group>255||priority<1||priority>254
                    ||advertisementTicks<20||advertisementTicks>200||advertisementTicks%20!=0
                    ||address.equals(vip)||!Ipv4.sameSubnet(address,vip,prefix)||!VrrpSettings.hostAddress(address,prefix)||!VrrpSettings.hostAddress(vip,prefix))throw new IllegalArgumentException("VRRP設定不正");
        }
    }
    public record Advertisement(String sender,String address,String vip,int group,int priority,int intervalTicks,int ttl){}
    public record Status(State state,String master,long deadlineInTicks,long sent,long received,String reason){}
    public interface Transport {
        List<String> peers(String sender);
        void log(String id,String message);
        void ownershipChanged(String id);
    }
    private static final class Router {
        final Configuration config;
        final long generation;
        State state=State.BACKUP;
        String master="",reason="起動: 広告を待機";
        long sent,received;
        int masterInterval;
        Timer timer;
        Router(Configuration config,long generation){this.config=config;this.generation=generation;masterInterval=config.advertisementTicks;}
    }
    private record Timer(long due,String id){}
    private record Delivery(long due,long sequence,String receiver,long generation,Advertisement advertisement){}
    private final Transport transport;
    private final Map<String,Router> routers=new LinkedHashMap<>();
    private final NavigableSet<Timer> timers=new TreeSet<>(Comparator.comparingLong(Timer::due).thenComparing(Timer::id));
    private final PriorityQueue<Delivery> deliveries=new PriorityQueue<>(Comparator.comparingLong(Delivery::due).thenComparingLong(Delivery::sequence));
    private long now,generation,sequence;
    private int lastProcessed;
    private String error="";
    public VrrpEngine(Transport transport){this.transport=Objects.requireNonNull(transport);}
    public String error(){return error;}
    public int queuedEvents(){return timers.size()+deliveries.size();}
    public int lastProcessed(){return lastProcessed;}
    public Configuration configuration(String id){var r=routers.get(id);return r==null?null:r.config;}
    public boolean isMaster(String id){var r=routers.get(id);return error.isEmpty()&&r!=null&&r.state==State.MASTER;}
    public Status status(String id){var r=routers.get(id);return r==null?new Status(State.INITIALIZE,"",0,0,0,error.isEmpty()?"無効・故障・ポート不適合・未初期化":error):
        new Status(r.state,r.master,r.timer==null?0:Math.max(0,r.timer.due-now),r.sent,r.received,r.reason);}
    public static long skewTicks(int priority,int interval){return Math.max(1,((256L-priority)*interval+255)/256);}
    public static long downTicks(int priority,int interval){return 3L*interval+skewTicks(priority,interval);}
    public void synchronize(Map<String,Configuration> configs){
        if(configs.size()>MAX_ROUTERS){stop("VRRP有効機器は128台までです");return;}
        error="";
        for(String id:new ArrayList<>(routers.keySet()))if(!Objects.equals(routers.get(id).config,configs.get(id)))remove(id);
        for(var entry:configs.entrySet())if(!routers.containsKey(entry.getKey())){
            var r=new Router(entry.getValue(),++generation);routers.put(entry.getKey(),r);
            setTimer(entry.getKey(),r,downTicks(r.config.priority,r.masterInterval));
            transport.log(entry.getKey(),"VRRP BACKUP: 広告待機 / VRID "+r.config.group);
        }
    }
    private void remove(String id){var old=routers.remove(id);if(old!=null){if(old.timer!=null)timers.remove(old.timer);if(old.state==State.MASTER)transport.ownershipChanged(id);}}
    /** 管理操作の停止のみ。故障・破壊・リンク断ではこの広告を送らない。 */
    public void shutdown(String id){var r=routers.get(id);if(r==null)return;if(r.state==State.MASTER)send(id,r,0);remove(id);}
    public void stop(String reason){
        error=reason;for(String id:new ArrayList<>(routers.keySet())){transport.log(id,"VRRP停止: "+reason);remove(id);}timers.clear();deliveries.clear();
    }
    private void setTimer(String id,Router r,long after){
        if(routers.get(id)!=r)return;
        if(r.timer!=null)timers.remove(r.timer);
        r.timer=new Timer(now+Math.max(1,after),id);timers.add(r.timer);
    }
    private void send(String id,Router r,int priority){
        List<String> peers=transport.peers(id);
        if(peers.size()>MAX_PEERS){stop("VRRP広告の受信候補上限です");return;}
        if(deliveries.size()+peers.size()>MAX_DELIVERIES){stop("VRRP広告待ち行列の上限です");return;}
        r.sent++;
        var c=r.config;var ad=new Advertisement(id,c.address,c.vip,c.group,priority,c.advertisementTicks,255);
        for(String peer:peers){Router to=routers.get(peer);if(to!=null&&!id.equals(peer))deliveries.add(new Delivery(now+1,++sequence,peer,to.generation,ad));}
    }
    /** 受信広告を検査する入口。ワイヤ列や外部ソケットではなく不変メタデータを扱う。 */
    public void receive(String receiver,Advertisement ad){
        Router r=routers.get(receiver);if(r==null||!error.isEmpty()||ad==null)return;
        var c=r.config;
        if(ad.ttl!=255||ad.group!=c.group||!c.vip.equals(ad.vip)||ad.priority<0||ad.priority>254
                ||ad.sender==null||ad.sender.isBlank()||ad.intervalTicks<20||ad.intervalTicks>200||ad.intervalTicks%20!=0||receiver.equals(ad.sender))return;
        final long primary;
        try{primary=VrrpSettings.unicast(ad.address);if(!VrrpSettings.hostAddress(ad.address,c.prefix)||!Ipv4.sameSubnet(c.address,ad.address,c.prefix)||ad.address.equals(c.address))return;}
        catch(IllegalArgumentException bad){return;}
        r.received++;
        if(r.state==State.BACKUP){
            if(ad.priority==0){r.reason="priority 0: 管理停止を受信";setTimer(receiver,r,skewTicks(c.priority,r.masterInterval));}
            else if(!c.preempt||ad.priority>=c.priority){r.master=ad.address;r.masterInterval=ad.intervalTicks;r.reason="広告受信: "+ad.address;setTimer(receiver,r,downTicks(c.priority,r.masterInterval));}
            return;
        }
        boolean superior=ad.priority>c.priority||ad.priority==c.priority&&primary>Ipv4.parse(c.address);
        if(ad.priority!=0&&superior){
            r.state=State.BACKUP;r.master=ad.address;r.masterInterval=ad.intervalTicks;r.reason="上位Masterの広告を受信";
            transport.log(receiver,"VRRP MASTER → BACKUP: "+ad.address);transport.ownershipChanged(receiver);
            setTimer(receiver,r,downTicks(c.priority,r.masterInterval));
        }else{
            send(receiver,r,c.priority);
            if(ad.priority==0)setTimer(receiver,r,c.advertisementTicks);
        }
    }
    /** 1呼出しのイベント処理上限を守る。通常サーバーはadvance(1)で呼ぶ。 */
    public void advance(long ticks){
        lastProcessed=0;if(ticks<=0)return;
        long end=Math.addExact(now,ticks);
        while(error.isEmpty()&&lastProcessed<MAX_EVENTS_PER_TICK){
            long timerDue=timers.isEmpty()?Long.MAX_VALUE:timers.first().due;
            long deliveryDue=deliveries.isEmpty()?Long.MAX_VALUE:deliveries.peek().due;
            long next=Math.min(timerDue,deliveryDue);if(next>end||next==Long.MAX_VALUE)break;
            now=Math.max(now,next);lastProcessed++;
            // 同tickなら到着広告を先に処理し、不要な一時Master化を減らす。
            if(deliveryDue<=timerDue){var d=deliveries.remove();var r=routers.get(d.receiver);if(r!=null&&r.generation==d.generation)receive(d.receiver,d.advertisement);}
            else{
                var timer=timers.pollFirst();var r=routers.get(timer.id);if(r==null||r.timer!=timer)continue;r.timer=null;
                if(r.state==State.BACKUP){r.state=State.MASTER;r.master=r.config.address;r.reason="Master_Down期限が満了";transport.log(timer.id,"VRRP BACKUP → MASTER: 広告途絶 / VRID "+r.config.group);transport.ownershipChanged(timer.id);}
                send(timer.id,r,r.config.priority);setTimer(timer.id,r,r.config.advertisementTicks);
            }
        }
        now=end;
    }
}
