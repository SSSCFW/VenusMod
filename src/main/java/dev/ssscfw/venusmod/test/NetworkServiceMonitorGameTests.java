package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 通常の実配線・サーバーtickを使う。監視結果とNAT表を直接注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkServiceMonitorGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;return d;
    }
    private static void wire(GameTestHelper h,int x){
        var pos=h.absolutePos(new BlockPos(x,2,5));var cable=NetworkContent.CABLES.get(CableGrade.CAT5E).get();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(cable.defaultBlockState(),h.getLevel(),pos),3);
    }
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String command){
        var r=NetworkServiceCommands.run(w.simulator(),d.id(),command);h.assertTrue(r.handled()&&r.changed(),command+" / "+r.lines());
        d.adoptProfile(w.simulator().nodes().get(d.id()));
        if(command.startsWith("monitor"))d.setChanged();else d.refreshState();
    }
    private static MonitorRuntime.Snapshot state(NetworkWorld w,NetworkDeviceEntity d,String label){
        return w.simulator().monitor().snapshots(d.id()).stream().filter(x->x.label().equals(label)).findFirst().orElseThrow();
    }
    private static boolean up(NetworkWorld w,NetworkDeviceEntity d,String label){return state(w,d,label).state()==MonitorRuntime.State.UP;}
    private static void router(NetworkDeviceEntity r,String west,String east){
        r.profile().dhcp=false;for(var p:r.profile().ports)p.address="";r.profile().ports[3].address=west;r.profile().ports[1].address=east;
    }
    @GameTest(template="test/wide_empty",batch="network_service_monitor",timeoutTicks=170)
    public static void serviceStopDoesNotBecomeIcmpFailure(GameTestHelper h){
        var m=device(h,DeviceKind.SERVER,1,5,"10.98.1.10");m.profile().gateway="10.98.1.1";
        var r=device(h,DeviceKind.ROUTER,5,5,"");router(r,"10.98.1.1","10.98.2.1");
        var target=device(h,DeviceKind.SERVER,9,5,"10.98.2.20");
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);var w=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready(),"実配線を解析");config(h,w,r,"nat port 3 inside");config(h,w,r,"nat port 1 outside");config(h,w,r,"nat on");
            config(h,w,m,"monitor add host 10.98.2.20");config(h,w,m,"monitor add site web 10.98.2.20");
            config(h,w,m,"monitor interval 1");config(h,w,m,"monitor thresholds 1 1");config(h,w,m,"monitor on");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(up(w,m,"host")&&!up(w,m,"site"),"pingは正常、Web停止はDOWN");target.profile().web=true;target.setChanged();
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(up(w,m,"host")&&up(w,m,"site"),"Webサービスの復旧を検出");
            config(h,w,r,"acl add 3 in 10 deny icmp any any");config(h,w,r,"acl add 3 in 20 permit ip any any");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(!up(w,m,"host")&&up(w,m,"site"),"ICMP禁止をWeb停止と誤認しない");
            config(h,w,r,"acl add 3 in 15 deny tcp any any 80");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(!up(w,m,"site")&&state(w,m,"site").detail().contains("ACL"),"TCP80拒否で理由付きDOWN");
            config(h,w,r,"acl clear 3 in");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(up(w,m,"host")&&up(w,m,"site"),"ACL修正後に自動復旧");
            target.profile().web=false;target.setChanged();
        }).thenIdle(22).thenExecute(()->h.assertTrue(up(w,m,"host")&&!up(w,m,"site"),"本体を止めずサービスだけの停止を再検出")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_service_monitor_save",timeoutTicks=100)
    public static void forwardedControlProbeAndDeviceDrop(GameTestHelper h){
        var m=device(h,DeviceKind.SERVER,1,5,"10.99.0.10");var r=device(h,DeviceKind.ROUTER,5,5,"");router(r,"10.99.0.1","10.100.0.1");
        var target=device(h,DeviceKind.REDSTONE_TERMINAL,9,5,"10.100.0.20");target.profile().gateway="10.100.0.1";target.setRedstone(11);
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);var w=NetworkWorld.get(h.getLevel());
        var owner=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"service-owner"));
        var guest=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"service-guest"));
        owner.setPos(m.getBlockPos().getX()+0.5,m.getBlockPos().getY()+0.5,m.getBlockPos().getZ()+0.5);guest.setPos(owner.getX(),owner.getY(),owner.getZ());m.setOwnerIfAbsent(owner);target.setOwnerIfAbsent(guest);
        h.startSequence().thenIdle(15).thenExecute(()->{
            config(h,w,r,"nat port 3 outside");config(h,w,r,"nat port 1 inside");config(h,w,r,"nat on");
            config(h,w,r,"nat forward add 3 tcp 3000 10.100.0.20 25570");config(h,w,r,"nat forward add 3 tcp 8080 10.100.0.20 80");
            config(h,w,m,"monitor add signal control 10.99.0.1:3000");config(h,w,m,"monitor add wrong web 10.99.0.1:8080");
            config(h,w,m,"monitor interval 1");config(h,w,m,"monitor thresholds 1 1");
            var menu=new NetworkMenu(64,owner.getInventory(),m);owner.containerMenu=menu;menu.command(owner,"monitor on");
            h.assertTrue(m.profile().monitor.enabled,"実メニュー経由で監視開始");m.profile().publicService=true;
            var forbidden=new NetworkMenu(65,guest.getInventory(),m);guest.containerMenu=forbidden;forbidden.command(guest,"monitor clear");
            h.assertTrue(m.profile().monitor.targets.size()==2&&!forbidden.snapshot().contains("signal"),"公開設定は監視一覧と編集を公開しない");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(up(w,m,"signal")&&!up(w,m,"wrong"),"制御端末は応答するがWebサーバーとは判定しない");
            h.assertTrue(target.redstone()==11&&w.simulator().flows().isEmpty(),"監視で出力やデータ転送を起こさない");
            h.assertTrue(w.simulator().nat().sessions(r.id()).size()==1,"非対応サービスの変換を確定しない");
            m.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));m.profile().failed=true;
            var drops=Block.getDrops(m.getBlockState(),h.getLevel(),m.getBlockPos(),m);h.assertTrue(drops.size()==1,"実ドロップは機器1個");
            var restored=device(h,DeviceKind.SERVER,2,8,"");restored.restoreItem(drops.getFirst());var p=restored.profile();
            h.assertTrue(p.monitor.services.get("signal").kind()==MonitorTarget.Kind.CONTROL&&p.monitor.services.get("signal").port()==3000,"種類と公開ポートを保存復元");
            h.assertTrue(p.failed&&p.ports[1].moduleTier==2&&restored.canUse(owner)&&!restored.canUse(guest),"装着品・故障・所有者を保持");
            h.assertTrue(state(w,restored,"signal").state()==MonitorRuntime.State.UNKNOWN&&state(w,restored,"signal").probes()==0,"動的な監視結果は持ち越さない");
        }).thenSucceed();
    }
}
