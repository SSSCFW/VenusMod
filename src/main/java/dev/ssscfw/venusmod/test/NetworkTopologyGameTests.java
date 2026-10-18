package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import net.neoforged.neoforge.gametest.*;

/** 通常の配線イベント/サーバーtickで局所性を検査。論理リンク・役割は注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkTopologyGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        var p=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(p,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var device=(NetworkDeviceEntity)h.getLevel().getBlockEntity(p);
        device.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));return device;
    }
    private static void wire(GameTestHelper h,int x,int z){var p=h.absolutePos(new BlockPos(x,2,z));var s=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();h.getLevel().setBlock(p,Block.updateFromNeighbourShapes(s,h.getLevel(),p),3);}
    private static void cut(GameTestHelper h,int x,int z){h.getLevel().removeBlock(h.absolutePos(new BlockPos(x,2,z)),false);}
    private static void command(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String command){var reply=NetworkServiceCommands.run(w.simulator(),d.id(),command);h.assertTrue(reply.changed(),command+" / "+reply.lines());d.adoptProfile(w.simulator().nodes().get(d.id()));d.refreshState();}
    @GameTest(template="test/wide_empty",batch="network_topology",timeoutTicks=160)
    public static void separateCableEditsPreserveTransferNatAndEngine(GameTestHelper h){
        var pc=device(h,DeviceKind.PC,1,2);var r=device(h,DeviceKind.ROUTER,5,2);var server=device(h,DeviceKind.SERVER,10,2);
        var x=device(h,DeviceKind.PC,1,9);var y=device(h,DeviceKind.PC,10,9);
        pc.profile().address="10.102.1.10";pc.profile().gateway="10.102.1.1";r.profile().dhcp=false;
        for(var p:r.profile().ports)p.address="";r.profile().ports[3].address="10.102.1.1";r.profile().ports[1].address="203.0.113.1";
        server.profile().address="203.0.113.10";x.profile().address="192.0.2.1";y.profile().address="192.0.2.2";
        for(int a=2;a<10;a++){if(a!=5)wire(h,a,2);wire(h,a,9);}
        var world=NetworkWorld.get(h.getLevel());var engine=world.simulator();var flow=new NetworkSimulator.Flow[1];long[] binding={0};double[] remaining={0};
        h.startSequence().thenIdle(20).thenExecute(()->{
            h.assertTrue(world.ready(),"通常配線の局所解析完了: "+world.diagnostic());
            command(h,world,r,"nat port 3 inside");command(h,world,r,"nat port 1 outside");command(h,world,r,"nat on");
            var result=engine.startTransfer(pc.id(),server.profile().address,100_000_000_000L);h.assertTrue(result.success(),"NAT経由の転送開始: "+result.message());
            flow[0]=engine.flows().stream().filter(f->f.source.equals(pc.id())).findFirst().orElseThrow();remaining[0]=flow[0].remaining;binding[0]=engine.nat().sessions(r.id()).getFirst().id();
            cut(h,4,9);
            h.assertTrue(world.simulator()==engine,"エンジンを使い捨てない");h.assertTrue(engine.flows().contains(flow[0])&&engine.nat().alive(binding[0]),"別網の断線で転送・NAT維持");
            h.assertTrue(!engine.ping(x.id(),y.profile().address).success(),"切った区間は解析完了前に即時停止");
            h.assertTrue(engine.ping(pc.id(),server.profile().address).success(),"別網は解析中でも利用可能");
        }).thenIdle(15).thenExecute(()->{
            h.assertTrue(engine.flows().contains(flow[0])&&flow[0].remaining<remaining[0]&&engine.nat().alive(binding[0]),"走査中も継続転送");wire(h,4,9);
        }).thenIdle(15).thenExecute(()->{
            h.assertTrue(engine.ping(x.id(),y.profile().address).success(),"再配線側も復旧");h.assertTrue(engine.flows().contains(flow[0])&&engine.nat().alive(binding[0]),"別網再接続でNAT保持");
            cut(h,3,2);h.assertTrue(!engine.flows().contains(flow[0])&&!engine.nat().alive(binding[0]),"自分の経路の断線は即時失効");
            h.assertTrue(engine.ping(x.id(),y.profile().address).success(),"反対側の網を止めない");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_topology",timeoutTicks=300)
    public static void unrelatedPhysicalCutDoesNotRestartVrrpClock(GameTestHelper h){
        var r=device(h,DeviceKind.ROUTER,2,2);var pc=device(h,DeviceKind.PC,8,2);var x=device(h,DeviceKind.PC,2,9);var y=device(h,DeviceKind.PC,8,9);
        for(var p:r.profile().ports)p.address="";r.profile().ports[1].address="10.103.1.1";r.profile().vip="10.103.1.254";r.profile().vrrp.port=1;r.profile().vrrp.enabled=true;r.profile().dhcp=false;pc.profile().address="10.103.1.10";
        x.profile().address="10.103.2.1";y.profile().address="10.103.2.2";for(int a=3;a<8;a++){wire(h,a,2);wire(h,a,9);}
        var world=NetworkWorld.get(h.getLevel());var engine=world.simulator();long[] sent={0},deadline={0};
        h.startSequence().thenIdle(130).thenExecute(()->{
            h.assertTrue(engine.vrrpMaster(r.id()),"広告期限後にMaster");var s=engine.vrrp().status(r.id());sent[0]=s.sent();deadline[0]=s.deadlineInTicks();cut(h,4,9);
            h.assertTrue(world.simulator()==engine&&engine.vrrpMaster(r.id()),"無関係なケーブルでVRRPを再起動しない");h.assertTrue(engine.vrrp().status(r.id()).deadlineInTicks()==deadline[0],"操作自体で期限を動かさない");
            h.assertTrue(engine.ping(pc.id(),r.profile().vip).success(),"同tickでVIPを維持");
        }).thenIdle(45).thenExecute(()->{
            h.assertTrue(world.simulator()==engine&&engine.vrrpMaster(r.id())&&engine.vrrp().status(r.id()).sent()>sent[0],"周期広告/時計を継続");
            h.assertTrue(engine.ping(pc.id(),r.profile().vip).success(),"解析後も通信可能");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_topology",timeoutTicks=120)
    public static void restoredIdentityLeavesNoGhostNode(GameTestHelper h){
        var r=device(h,DeviceKind.ROUTER,2,2);var pc=device(h,DeviceKind.PC,8,2);for(int a=3;a<8;a++)wire(h,a,2);
        var world=NetworkWorld.get(h.getLevel());String[] temporary={""};NetworkDeviceEntity[] copy={null};int[] count={0};
        h.startSequence().thenIdle(15).thenExecute(()->{
            count[0]=world.simulator().nodes().size();var packed=r.savedItem();copy[0]=device(h,DeviceKind.ROUTER,2,8);
            // 通常の登録後に既存アイテムのIDへ変わるケースを再現する。
            world.add(copy[0]);temporary[0]=copy[0].id();copy[0].restoreItem(packed);
        }).thenIdle(15).thenExecute(()->{
            h.assertTrue(world.simulator().nodes().size()==count[0]+1,"再配置の仮IDをエンジンに残さない");
            h.assertTrue(!world.simulator().nodes().containsKey(temporary[0]),"置換前IDを撤去");
            h.assertTrue(!r.id().equals(copy[0].id()),"同時に存在するコピーは別ID");
        }).thenSucceed();
    }
}
