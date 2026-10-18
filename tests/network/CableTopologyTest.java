import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** Javaだけの仮想ブロック世界。未ロードの読み出し・全域再走査・古いリンク公開を検出する。 */
public final class CableTopologyTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    record Pos(int x,int y,int z){}
    static final class World implements CableTopology.View<Pos>,CableTopology.Listener {
        final Map<Pos,CableGrade> cables=new HashMap<>();final Map<Pos,CableTopology.Node> devices=new HashMap<>();
        final Map<String,NetworkSimulator.Link> links=new LinkedHashMap<>();final Set<Long> unloaded=new HashSet<>();final Set<Pos> reads=new HashSet<>();
        final CableTopology<Pos> topology=new CableTopology<>(this,this);int mutations;
        public Pos adjacent(Pos p,int n){return switch(n){case 0->new Pos(p.x,p.y,p.z-1);case 1->new Pos(p.x+1,p.y,p.z);case 2->new Pos(p.x,p.y,p.z+1);case 3->new Pos(p.x-1,p.y,p.z);case 4->new Pos(p.x,p.y+1,p.z);case 5->new Pos(p.x,p.y-1,p.z);default->throw new IllegalArgumentException();};}
        public long chunk(Pos p){return ((long)(p.x>>4)<<32)|Integer.toUnsignedLong(p.z>>4);}
        public boolean loaded(Pos p){return !unloaded.contains(chunk(p));}
        private void read(Pos p){if(!loaded(p))throw new AssertionError("未ロードを参照: "+p);reads.add(p);}
        public CableGrade cable(Pos p){read(p);return cables.get(p);}
        public CableTopology.Node device(Pos p){read(p);return devices.get(p);}
        public void connect(NetworkSimulator.Link link){if(links.putIfAbsent(link.id(),link)!=null)throw new AssertionError("二重リンク");mutations++;}
        public void disconnect(String id){links.remove(id);mutations++;}
        void node(int x,int z,String id){var p=new Pos(x,0,z);devices.put(p,new CableTopology.Node(id,DeviceKind.PC));topology.changed(p);}
        void cable(int x,int z,CableGrade grade){var p=new Pos(x,0,z);if(grade==null)cables.remove(p);else cables.put(p,grade);topology.changed(p);}
        void settle(){for(int t=0;t<20000&&!topology.idle();t++){topology.step(8);ok(topology.lastSteps()<=8,"tick当たりの作業上限");}ok(topology.idle(),"局所走査が終了");ok(topology.error().isEmpty(),"解析エラーなし: "+topology.error());}
        void line(int start,int end,int z,String id){node(start,z,id+"a");node(end,z,id+"b");for(int x=start+1;x<end;x++)cable(x,z,CableGrade.CAT6A);}
    }
    public static void main(String[] args){
        var w=new World();w.line(0,8,0,"A");w.line(100,108,0,"B");w.settle();ok(w.links.size()==2,"2本の独立回線");
        var b=w.links.values().stream().filter(l->l.a().startsWith("B")).findFirst().orElseThrow();
        w.reads.clear();w.cable(4,0,null);ok(w.links.size()==1&&w.links.containsKey(b.id()),"断線はtickを待たず停止、別リンク保持");w.settle();
        ok(w.reads.stream().noneMatch(p->p.x>90),"別配線のブロックを再読込しない");
        w.cable(4,0,CableGrade.CAT6A);w.settle();ok(w.links.size()==2,"切断後の再接続");
        w.cable(4,1,CableGrade.CAT6A);ok(w.links.size()==1,"分岐追加時の即時停止");w.settle();ok(w.links.size()==1,"T字分岐は公開しない");
        w.cable(4,1,null);w.settle();ok(w.links.size()==2,"枝を除けば復旧");
        w.cable(4,0,CableGrade.CAT5E);w.settle();ok(w.links.size()==1,"ケーブル異種混在は拒否");w.cable(4,0,CableGrade.CAT6A);w.settle();
        // 予算1で長い走査を進め、走査済みの位置を再編集しても古い確定結果を使わない。
        w.line(200,270,0,"C");for(int i=0;i<40;i++)w.topology.step(1);w.cable(203,0,null);w.settle();ok(w.links.size()==2,"走査途中で切った回線が復活しない");
        w.cable(203,0,CableGrade.CAT6A);w.settle();ok(w.links.size()==3,"走査中断後の復旧");
        // 途中チャンクのアンロード。未ロードはworld read()で例外になる。
        long ch=w.chunk(new Pos(240,0,0));w.unloaded.add(ch);w.topology.chunkChanged(ch,false);ok(w.links.size()==2,"チャンク停止も即時に無効化");w.settle();ok(w.links.size()==2,"未ロードを強制参照しない");
        w.unloaded.remove(ch);w.topology.chunkChanged(ch,true);w.settle();ok(w.links.size()==3,"ロード後に再構成");
        var old=w.devices.remove(new Pos(200,0,0));w.topology.changed(new Pos(200,0,0));w.settle();ok(w.links.size()==2,"端点を消すと通信不能");w.node(200,0,"C-new");w.settle();ok(w.links.values().stream().noneMatch(l->l.a().equals(old.id())||l.b().equals(old.id())),"古いIDを使わない");
        int active=w.topology.trackedBlocks();for(int n=0;n<60;n++){w.cable(4,0,null);w.topology.step(2);w.cable(4,0,CableGrade.CAT6A);w.settle();}ok(w.topology.trackedBlocks()==active,"編集反復で索引が膨張しない");
        // 端点がなく、閉じた輪でも有限に終わる。
        var loop=new World();for(int x=0;x<4;x++){loop.cable(x,0,CableGrade.CAT5E);loop.cable(x,3,CableGrade.CAT5E);}for(int z=1;z<3;z++){loop.cable(0,z,CableGrade.CAT5E);loop.cable(3,z,CableGrade.CAT5E);}loop.settle();ok(loop.links.isEmpty(),"端点なしループは有限に拒否");
        var longLine=new World();longLine.line(0,132,0,"L");longLine.settle();ok(longLine.links.isEmpty(),"距離超過は拒否");
        System.out.println("CableTopologyTest: "+checks+" checks passed (budget checks include per-tick repetitions)");
    }
}
