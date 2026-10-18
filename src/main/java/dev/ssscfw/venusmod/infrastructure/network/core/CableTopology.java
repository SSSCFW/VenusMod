package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/**
 * Tickを持たない細いケーブルの局所索引。Pは不変な座標（本体ではBlockPos）。
 * セグメントのリンクは編集/アンロード時に即時撤去し、予算内の再走査後だけ公開する。
 * 各ブロックを毎tick巡回せず、破損した区間の旧座標も遅延列で処理する。
 */
public final class CableTopology<P> {
    public static final int MAX_BLOCKS=131072, MAX_PENDING=131072;
    private static final int[] OPPOSITE={2,3,0,1,5,4};
    /** 空IDは未ロード/登録上限の物理端点。配線を通す端点としては使わない。 */
    public record Node(String id,DeviceKind kind){public Node{Objects.requireNonNull(id);Objects.requireNonNull(kind);}}
    private record End(String id,DeviceKind kind,int port){}
    public interface View<P> {
        P adjacent(P pos,int port);
        long chunk(P pos);
        boolean loaded(P pos);
        CableGrade cable(P pos);
        /** -1 は無染色。DyeColorの16個のIDをそのまま使用する。 */
        default int color(P pos){return -1;}
        Node device(P pos);
    }
    public interface Listener {
        void connect(NetworkSimulator.Link link);
        void disconnect(String link);
    }
    private final View<P> world;
    private final Listener listener;
    private final Map<P,Segment> owners=new HashMap<>();
    private final Set<Segment> segments=new HashSet<>();
    private final Map<Long,Set<Segment>> watchers=new HashMap<>();
    private final LinkedHashSet<P> seeds=new LinkedHashSet<>();
    private final Map<Long,Integer> seedChunks=new HashMap<>();
    private final ArrayDeque<Iterator<P>> deferred=new ArrayDeque<>();
    private final Map<Long,Set<P>> waiting=new HashMap<>();
    private final Set<Long> unavailable=new HashSet<>();
    private Segment current;
    private int lastSteps,connections,rejected,waitingCount;
    private long totalSteps;
    private String error="";
    private final class Segment {
        final CableGrade grade;
        final int color;
        final Set<P> cells=new LinkedHashSet<>(),queued=new HashSet<>();
        final ArrayDeque<P> frontier=new ArrayDeque<>();
        final Set<End> ends=new LinkedHashSet<>();
        final Set<Long> chunks=new HashSet<>();
        boolean live=true,branched,incomplete,finished;
        String link;
        Segment(CableGrade grade,int color,P start){this.grade=grade;this.color=color;enqueue(start);}
        void enqueue(P pos){if(queued.add(pos))frontier.addLast(pos);}
    }
    public CableTopology(View<P> world,Listener listener){this.world=Objects.requireNonNull(world);this.listener=Objects.requireNonNull(listener);}
    public boolean idle(){return !error.isEmpty()||current==null&&seeds.isEmpty()&&deferred.isEmpty();}
    public String error(){return error;}
    public int lastSteps(){return lastSteps;}
    public long totalSteps(){return totalSteps;}
    public int trackedBlocks(){return owners.size();}
    public int segmentCount(){return segments.size();}
    public int connections(){return connections;}
    public String diagnostic(){return error.isEmpty()?"接続 "+connections+"本 / 不成立 "+rejected+"区間 / 索引 "+owners.size()+"ブロック / "+(idle()?"待機":"局所解析中"):error;}
    private boolean loaded(P pos){return !unavailable.contains(world.chunk(pos))&&world.loaded(pos);}
    private void seed(P pos){
        if(!error.isEmpty()||seeds.contains(pos))return;
        if(seeds.size()>=MAX_PENDING){stop("配線変更の待ち行列上限。配線を減らし管理者が topology rebuild を実行してください");return;}
        seeds.add(pos);seedChunks.merge(world.chunk(pos),1,Integer::sum);
    }
    /** 設置/破壊位置と6近傍の区間だけを失効。空中の無関係な配線は全探索しない。 */
    public void changed(P pos){
        if(!error.isEmpty())return;
        Set<P> around=new LinkedHashSet<>();around.add(pos);for(int n=0;n<6;n++)around.add(world.adjacent(pos,n));
        for(P p:around){Segment segment=owners.get(p);if(segment!=null)retire(segment);}
        for(P p:around)seed(p);
    }
    /** ChunkEventではhasChunkAtがまだtrueでも、そのチャンクを読まない。 */
    public void chunkChanged(long chunk,boolean isLoaded){
        if(!error.isEmpty())return;
        if(isLoaded)unavailable.remove(chunk);
        else if(watchers.containsKey(chunk)||waiting.containsKey(chunk)||seedChunks.containsKey(chunk))unavailable.add(chunk);
        if(unavailable.size()>MAX_BLOCKS){stop("未ロード依存の上限です。管理者が topology rebuild を実行してください");return;}
        var affected=watchers.get(chunk);if(affected!=null)for(Segment segment:new ArrayList<>(affected))retire(segment);
        if(isLoaded){var positions=waiting.remove(chunk);if(positions!=null){waitingCount-=positions.size();for(P p:positions)seed(p);}}
    }
    private void watch(Segment segment,P pos){long chunk=world.chunk(pos);if(segment.chunks.add(chunk))watchers.computeIfAbsent(chunk,k->new HashSet<>()).add(segment);}
    private void retire(Segment segment){
        if(!segment.live)return;segment.live=false;segments.remove(segment);
        if(segment.link!=null){listener.disconnect(segment.link);segment.link=null;connections--;}
        else if(segment.finished)rejected--;
        for(long chunk:segment.chunks){var set=watchers.get(chunk);if(set!=null){set.remove(segment);if(set.isEmpty())watchers.remove(chunk);}}
        // 8192本分等の座標をイベントの1回で走査/コピーしない。
        if(!segment.cells.isEmpty())deferred.addLast(segment.cells.iterator());
        if(!segment.frontier.isEmpty())deferred.addLast(segment.frontier.iterator());
        if(current==segment)current=null;
        if(deferred.size()>MAX_PENDING)stop("配線再解析の作業数上限です。管理者が topology rebuild を実行してください");
    }
    private P nextSeed(){
        if(!seeds.isEmpty()){var it=seeds.iterator();P pos=it.next();it.remove();seedChunks.computeIfPresent(world.chunk(pos),(k,n)->n==1?null:n-1);return pos;}
        // 空のiteratorを捨てるのも1ステップ。大量編集でも掃除だけでtickを占有しない。
        if(!deferred.isEmpty()){var it=deferred.peekFirst();if(it.hasNext())return it.next();deferred.removeFirst();}
        return null;
    }
    private void waitForChunk(P pos){
        long chunk=world.chunk(pos);var set=waiting.computeIfAbsent(chunk,k->new LinkedHashSet<>());
        if(set.add(pos)&&++waitingCount>MAX_PENDING)stop("ロード待ちの配線数上限です。管理者が topology rebuild を実行してください");
    }
    /** 1 stepで最大1ブロックと6近傍だけを参照。上限は呼出側のサーバー設定から渡す。 */
    public void step(int budget){
        if(budget<1)throw new IllegalArgumentException("配線処理予算は1以上です");lastSteps=0;
        while(lastSteps<budget&&!idle()){
            lastSteps++;totalSteps++;
            if(current==null){
                P pos=nextSeed();if(pos==null)continue;
                Segment known=owners.get(pos);if(known!=null&&known.live)continue;
                if(known!=null)owners.remove(pos);
                if(!loaded(pos)){waitForChunk(pos);continue;}
                CableGrade grade=world.cable(pos);if(grade==null)continue;
                current=new Segment(grade,world.color(pos),pos);segments.add(current);
            }
            Segment scan=current;
            if(scan.frontier.isEmpty()){finish(scan);current=null;continue;}
            P pos=scan.frontier.removeFirst();if(scan.cells.contains(pos))continue;
            Segment known=owners.get(pos);if(known!=null&&known!=scan&&known.live)retire(known);
            if(!error.isEmpty())return;
            watch(scan,pos);
            if(!loaded(pos)){scan.incomplete=true;waitForChunk(pos);continue;}
            if(world.cable(pos)!=scan.grade||world.color(pos)!=scan.color){scan.incomplete=true;continue;}
            if(!owners.containsKey(pos)&&owners.size()>=MAX_BLOCKS){stop("配線索引の上限 (131072ブロック)。配線を減らし管理者が topology rebuild を実行してください");return;}
            owners.put(pos,scan);scan.cells.add(pos);int degree=0;
            for(int n=0;n<6;n++){
                P adjacent=world.adjacent(pos,n);watch(scan,adjacent);
                if(!loaded(adjacent)){scan.incomplete=true;continue;}
                if(world.cable(adjacent)==scan.grade&&world.color(adjacent)==scan.color){degree++;scan.enqueue(adjacent);}
                else{Node node=world.device(adjacent);if(node!=null){degree++;if(node.id.isBlank())scan.incomplete=true;else scan.ends.add(new End(node.id,node.kind,OPPOSITE[n]));}}
            }
            if(degree>2)scan.branched=true;
        }
    }
    private void finish(Segment segment){
        segment.finished=true;
        if(!segment.incomplete&&!segment.branched&&segment.ends.size()==2&&segment.cells.size()<=segment.grade.maxLength()){
            var endpoints=new ArrayList<>(segment.ends);endpoints.sort(Comparator.comparing(e->e.id+":"+e.port));
            var a=endpoints.getFirst();var b=endpoints.getLast();
            if(!a.id.equals(b.id)){
                String id=a.id+":"+a.port+"-"+b.id+":"+b.port;
                boolean wan=a.kind==DeviceKind.ONU&&b.kind==DeviceKind.OLT||a.kind==DeviceKind.OLT&&b.kind==DeviceKind.ONU;
                try{listener.connect(new NetworkSimulator.Link(id,a.id,a.port,b.id,b.port,segment.grade,segment.cells.size(),wan));segment.link=id;connections++;}
                catch(IllegalArgumentException|IllegalStateException invalid){/* 端点削除/ポート競合/上限。不成立として保持し、次の変更で再検証。 */}
            }
        }
        if(segment.link==null)rejected++;
        // 走査済みの集合はcellsだけ残す。通常運転で待ち行列を抱えない。
        segment.queued.clear();
    }
    private void stop(String reason){
        error=reason;
        for(Segment segment:segments)if(segment.link!=null)listener.disconnect(segment.link);
        owners.clear();segments.clear();watchers.clear();seeds.clear();seedChunks.clear();deferred.clear();waiting.clear();unavailable.clear();
        current=null;connections=0;rejected=0;waitingCount=0;
    }
    /** 管理者の明示操作専用。古い索引を破棄してロード済み機器から再発見する。 */
    public void rebuild(Collection<P> devicePositions){stop("");for(P pos:devicePositions)changed(pos);}
}
