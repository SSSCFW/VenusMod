import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 期限ぴったり・切断・取消・処理上限・タイマー増殖を独立に検査する。 */
public final class ControlLeaseTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ServiceAddress IP=new ServiceAddress("10.99.0.20",25570);
    public static void main(String[] args){
        var r=new ControlLeaseRuntime();var expired=new ArrayList<String>();
        ok(r.accept("t","s",ACTOR,IP,100,0,false),"期限を開始");
        ok(r.active("t",99)&&!r.active("t",100),"期限ぴったりでは有効にしない");
        r.advance(100,x->{throw new AssertionError("単発に通信更新なし");},expired::add);
        ok(expired.equals(List.of("t"))&&r.leaseCount()==0&&r.expiryCount()==0,"失効通知は一度だけ");
        r.advance(101,x->ControlLeaseRuntime.Refresh.RECEIVED,expired::add);ok(expired.size()==1,"失効を繰り返し通知しない");
        ok(r.accept("t","s",ACTOR,IP,100,102,true),"定期維持を開始");
        for(long now=103;now<4000;now++)r.advance(now,x->ControlLeaseRuntime.Refresh.RECEIVED,expired::add);
        ok(r.active("t",4000)&&r.expiryCount()==1&&r.queueCount()==1,"繰返し更新でも期限は各一件");
        long deadline=4000+r.remaining("t",4000);r.advance(deadline,x->ControlLeaseRuntime.Refresh.RECEIVED,expired::add);
        ok(r.leaseCount()==0&&r.streamCount()==0&&expired.size()==2,"大きな時計ジャンプでも期限後に自動復活しない");
        ok(r.accept("t","s",ACTOR,IP,100,5000,true),"明示操作で再開");
        long before=r.remaining("t",5000);r.advance(5020,x->ControlLeaseRuntime.Refresh.RETRY,expired::add);
        ok(r.remaining("t",5020)==before-20,"通信失敗は期限を延ばさない");
        r.advance(5040,x->ControlLeaseRuntime.Refresh.RECEIVED,expired::add);ok(r.remaining("t",5040)==100,"期限前の復旧は維持");
        r.advance(5060,x->ControlLeaseRuntime.Refresh.CANCEL,expired::add);ok(r.streamCount()==0&&r.remaining("t",5060)==80,"権限失効は再送停止、期限は延長しない");
        r.advance(5140,x->ControlLeaseRuntime.Refresh.RECEIVED,expired::add);ok(r.leaseCount()==0,"取消後に期限切れ");
        r.accept("t","s",ACTOR,IP,100,5200,true);r.stopSource("s");ok(r.streamCount()==0&&r.active("t",5299),"送信停止は受信済み期限だけ保持");
        r.accept("t","s",ACTOR,IP,100,5300,true);r.removed("s");ok(r.streamCount()==0&&r.active("t",5301),"制御元アンロードで再送停止");
        r.accept("t","s",ACTOR,IP,100,5400,true);r.removed("t");ok(r.streamCount()==0&&r.leaseCount()==0,"受信端末アンロードで許可を持ち越さない");
        r.accept("t","s",ACTOR,IP,100,5500,true);r.accept("t","other",ACTOR,IP,100,5501,false);ok(r.streamCount()==0,"新しい単発命令が古い自動維持を停止");
        r=new ControlLeaseRuntime();for(int i=0;i<ControlLeaseRuntime.MAX_LEASES;i++)ok(r.accept("t"+i,"s",ACTOR,IP,100,0,false),"最大受信数内 "+i);
        ok(!r.accept("extra","s",ACTOR,IP,100,0,false)&&r.leaseCount()==128,"受信数上限で変更しない");
        r.advance(100,x->ControlLeaseRuntime.Refresh.RECEIVED,expired::add);ok(r.leaseCount()==0&&r.expiryCount()==0,"最大同時失効も回収");
        r=new ControlLeaseRuntime();for(int i=0;i<4;i++)r.accept("t"+i,"s",ACTOR,IP,100,0,true);
        ok(!r.accept("fifth","s",ACTOR,IP,100,0,true)&&r.queueCount()==4,"送信元4件の上限");
        ok(r.accept("t0","s",ACTOR,IP,100,1,true)&&r.queueCount()==4,"同じ宛先の置換は枠を増やさない");
        final int[] work={0};r.advance(20,x->{work[0]++;return ControlLeaseRuntime.Refresh.RECEIVED;},expired::add);
        r.advance(20,x->{work[0]++;return ControlLeaseRuntime.Refresh.RECEIVED;},expired::add);ok(work[0]==2,"同じtickに呼び直しても2件の予算");
        r.advance(21,x->{work[0]++;return ControlLeaseRuntime.Refresh.RECEIVED;},expired::add);ok(work[0]==4,"残りの送信元も順番に処理");
        r=new ControlLeaseRuntime();for(int i=0;i<64;i++)r.accept("t"+i,"s"+i,ACTOR,IP,100,0,true);
        ok(!r.accept("new","newsource",ACTOR,IP,100,0,true)&&r.streamCount()==64,"全体64維持の上限");
        r=new ControlLeaseRuntime();final var self=r;r.accept("t","s",ACTOR,IP,100,0,true);
        r.advance(20,x->{self.removed("t");return ControlLeaseRuntime.Refresh.RECEIVED;},expired::add);ok(r.streamCount()==0&&r.leaseCount()==0&&r.queueCount()==0,"処理途中の撤去を古い処理で復活させない");
        boolean bad=false;try{r.accept("t","s",ACTOR,IP,20,21,true);}catch(IllegalArgumentException e){bad=true;}ok(bad,"短すぎる定期維持を拒否");
        System.out.println("ControlLeaseTest: "+checks+" checks passed (128件は容量境界の登録確認)");
    }
}
