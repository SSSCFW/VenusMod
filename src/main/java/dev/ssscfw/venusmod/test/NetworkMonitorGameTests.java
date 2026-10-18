package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** 通常の配線・サーバーtick・実機器ドロップ。監視結果/期限はテスト側で注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkMonitorGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;return d;
    }
    private static void wire(GameTestHelper h,int x){var pos=h.absolutePos(new BlockPos(x,2,5));var block=NetworkContent.CABLES.get(CableGrade.CAT5E).get();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(block.defaultBlockState(),h.getLevel(),pos),3);}
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String input){
        var r=NetworkServiceCommands.run(w.simulator(),d.id(),input);h.assertTrue(r.handled()&&r.changed(),input+" / "+r.lines());d.adoptProfile(w.simulator().nodes().get(d.id()));
        if(input.startsWith("monitor")||input.startsWith("syslog"))d.setChanged();else d.refreshState();
    }
    private static MonitorRuntime.Snapshot state(NetworkWorld w,NetworkDeviceEntity d){return w.simulator().monitor().snapshots(d.id()).getFirst();}
    @GameTest(template="test/wide_empty",batch="network_monitor",timeoutTicks=230)
    public static void scheduledMonitoringNatAclFailureAndRecovery(GameTestHelper h){
        var m=device(h,DeviceKind.SERVER,1,5,"10.96.1.10");m.profile().gateway="10.96.1.1";
        var r=device(h,DeviceKind.ROUTER,5,5,"");r.profile().dhcp=false;for(var p:r.profile().ports)p.address="";r.profile().ports[3].address="10.96.1.1";r.profile().ports[1].address="10.96.2.1";
        var target=device(h,DeviceKind.PC,9,5,"10.96.2.20");
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);var w=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready(),"実配線解析");config(h,w,m,"monitor add base 10.96.2.20");config(h,w,m,"monitor interval 1");config(h,w,m,"monitor thresholds 1 1");config(h,w,m,"monitor on");
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.UNKNOWN,"開始だけで応答成功にしない");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.DOWN,"返信経路なしを検出");config(h,w,r,"nat port 3 inside");config(h,w,r,"nat port 1 outside");config(h,w,r,"nat on");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.UP&&!w.simulator().nat().sessions(r.id()).isEmpty(),"NAT変換した往復の定期確認");
            config(h,w,m,"syslog server on");config(h,w,m,"syslog target 10.96.1.10");config(h,w,m,"syslog on");
            h.getLevel().setBlock(h.absolutePos(new BlockPos(7,2,5)),Blocks.AIR.defaultBlockState(),3);
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.DOWN,"実ケーブル断を検出");h.assertTrue(w.simulator().syslog().entries(m.id()).stream().anyMatch(e->e.message().contains("MONITOR DOWN")),"障害イベントをSyslogへ送る");wire(h,7);
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.UP,"実配線復旧を検出");config(h,w,r,"acl add 3 in 10 deny icmp any any");config(h,w,r,"acl add 3 in 20 permit ip any any");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.DOWN&&state(w,m).detail().contains("ACL"),"ACLによる応答不可も区別せず理由を表示");config(h,w,r,"acl clear 3 in");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(state(w,m).state()==MonitorRuntime.State.UP,"ACL修復");m.profile().failed=true;m.refreshState();
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(w.simulator().monitor().describe(m.id()).getFirst().contains("PAUSED"),"監視元故障中は停止表示");h.assertTrue(state(w,m).state()==MonitorRuntime.State.UP,"監視元故障を全対象障害に変換しない");m.repair();
        }).thenIdle(22).thenExecute(()->h.assertTrue(state(w,m).state()==MonitorRuntime.State.UP,"監視元修理で再開")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_monitor_persistence",timeoutTicks=100)
    public static void monitorMenuPrivacyAndDeviceDrop(GameTestHelper h){
        var d=device(h,DeviceKind.SERVER,2,2,"10.97.0.20");var w=NetworkWorld.get(h.getLevel());
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"monitor-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"monitor-guest"),ClientInformation.createDefault());
        owner.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);guest.setPos(owner.getX(),owner.getY(),owner.getZ());d.setOwnerIfAbsent(owner);
        h.startSequence().thenIdle(10).thenExecute(()->{
            config(h,w,d,"monitor add private_target 10.97.0.21");config(h,w,d,"monitor interval 2");config(h,w,d,"monitor thresholds 2 3");
            var menu=new NetworkMenu(42,owner.getInventory(),d);owner.containerMenu=menu;menu.command(owner,"monitor on");h.assertTrue(d.profile().monitor.enabled,"所有者の実メニューで開始");
            d.profile().publicService=true;var forbidden=new NetworkMenu(43,guest.getInventory(),d);guest.containerMenu=forbidden;forbidden.command(guest,"monitor clear");
            h.assertTrue(d.profile().monitor.targets.containsKey("private_target")&&!forbidden.snapshot().contains("private_target"),"publicは監視一覧・設定変更権限を与えない");
            d.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));d.profile().failed=true;
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"機器1個に設定と装着品保存");var restored=device(h,DeviceKind.SERVER,6,2,"");restored.restoreItem(drops.getFirst());
            var p=restored.profile();h.assertTrue(p.monitor.enabled&&p.monitor.targets.get("private_target").equals("10.97.0.21")&&p.monitor.intervalSeconds==2&&p.monitor.failures==2&&p.monitor.recoveries==3,"監視設定を保存復元");
            h.assertTrue(p.failed&&p.ports[0].moduleTier==2&&restored.canUse(owner)&&!restored.canUse(guest),"装着品・故障・所有権を保持");
            h.assertTrue(state(w,restored).state()==MonitorRuntime.State.UNKNOWN&&state(w,restored).probes()==0,"過去の観測結果を複製しない");
        }).thenSucceed();
    }
}
