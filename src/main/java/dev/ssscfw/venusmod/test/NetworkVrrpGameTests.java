package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.neoforged.neoforge.gametest.*;

/** 論理リンクも役割も注入せず、実配線と通常サーバーtickで冗長化を検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkVrrpGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void wire(GameTestHelper h,int x,int z){var pos=h.absolutePos(new BlockPos(x,2,z));var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(state,h.getLevel(),pos),3);}
    private static void config(GameTestHelper h,NetworkWorld world,NetworkDeviceEntity d,String command){
        var reply=NetworkServiceCommands.run(world.simulator(),d.id(),command);h.assertTrue(reply.changed(),command+" / "+reply.lines());d.adoptProfile(world.simulator().nodes().get(d.id()));d.refreshState();
    }
    @GameTest(template="test/wide_empty",batch="network_vrrp",timeoutTicks=900)
    public static void timedFailoverPreemptionAndAdvertisementPartition(GameTestHelper h){
        var lan=device(h,DeviceKind.L2_SWITCH,6,6);var transit=device(h,DeviceKind.L2_SWITCH,6,10);
        var a=device(h,DeviceKind.ROUTER,2,6);var b=device(h,DeviceKind.ROUTER,10,6);
        var pc=device(h,DeviceKind.PC,6,2);var server=device(h,DeviceKind.SERVER,6,14);
        for(var r:List.of(a,b)){r.profile().dhcp=false;for(var port:r.profile().ports)port.address="";r.profile().vip="10.90.1.254";r.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));}
        a.profile().ports[1].address="10.90.1.1";a.profile().ports[2].address="10.90.2.1";a.profile().vrrp.port=1;a.profile().vrrpPriority=150;
        b.profile().ports[3].address="10.90.1.2";b.profile().ports[2].address="10.90.2.2";b.profile().vrrp.port=3;b.profile().vrrpPriority=100;
        pc.profile().address="10.90.1.10";pc.profile().gateway="10.90.1.254";
        server.profile().address="10.90.2.10";server.profile().gateway="10.90.2.2";
        for(int z=3;z<6;z++)wire(h,6,z);for(int x=3;x<6;x++)wire(h,x,6);for(int x=7;x<10;x++)wire(h,x,6);
        for(int z=7;z<=10;z++){wire(h,2,z);wire(h,10,z);}for(int x=3;x<6;x++)wire(h,x,10);for(int x=7;x<10;x++)wire(h,x,10);for(int z=11;z<14;z++)wire(h,6,z);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"実配線解析完了: "+world.diagnostic());h.assertTrue(!world.simulator().ping(pc.id(),"10.90.1.254").success(),"広告期限前はVIPを担当しない");
        }).thenIdle(100).thenExecute(()->{
            var sim=world.simulator();h.assertTrue(sim.vrrpMaster(a.id())&&!sim.vrrpMaster(b.id()),"広告でAをMasterに選出: "+sim.vrrp().status(a.id()));
            var data=sim.ping(pc.id(),server.profile().address);h.assertTrue(data.success()&&data.nodes().contains(a.id()),"VIP経由で別LANへ往復: "+data.message());
            h.assertTrue("00:00:5e:00:01:01".equals(sim.arpTable(pc.id()).get("10.90.1.254")),"仮想MACでARP応答");a.profile().failed=true;a.refreshState();
        }).thenIdle(10).thenExecute(()->h.assertTrue(!world.simulator().ping(pc.id(),"10.90.1.254").success(),"故障後も広告期限まで待つ"))
          .thenIdle(85).thenExecute(()->{
            var sim=world.simulator();h.assertTrue(sim.vrrpMaster(b.id()),"Bが期限後に引継ぎ");h.assertTrue(sim.ping(pc.id(),server.profile().address).success(),"B経由でも往復");
            config(h,world,a,"vrrp preempt off");a.repair();
        }).thenIdle(120).thenExecute(()->{
            h.assertTrue(world.simulator().vrrpMaster(b.id())&&!world.simulator().vrrpMaster(a.id()),"preempt offで現Masterを維持");config(h,world,a,"vrrp preempt on");
        }).thenIdle(120).thenExecute(()->{
            h.assertTrue(world.simulator().vrrpMaster(a.id())&&!world.simulator().vrrpMaster(b.id()),"preempt onで高優先度へ切り戻す");
            config(h,world,a,"acl add 1 in 10 deny vrrp any any");config(h,world,a,"acl add 1 in 20 permit ip any any");
            config(h,world,b,"acl add 3 in 10 deny vrrp any any");config(h,world,b,"acl add 3 in 20 permit ip any any");
        }).thenIdle(100).thenExecute(()->{
            var sim=world.simulator();h.assertTrue(sim.vrrpMaster(a.id())&&sim.vrrpMaster(b.id()),"広告の分断は二重Masterになる");
            var result=sim.ping(pc.id(),"10.90.1.254");h.assertTrue(!result.success()&&result.message().contains("競合"),"全体優先度で競合を隠さない");
            config(h,world,a,"acl clear 1 in");config(h,world,b,"acl clear 3 in");
        }).thenIdle(60).thenExecute(()->{
            h.assertTrue(world.simulator().ping(pc.id(),"10.90.1.254").destination().equals(a.id()),"広告の復旧で競合解消");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_vrrp",timeoutTicks=100)
    public static void settingsAndPermissionsSurviveDeviceDrop(GameTestHelper h){
        var a=device(h,DeviceKind.ROUTER,2,2);
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"vrrp-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"vrrp-guest"),ClientInformation.createDefault());
        a.setOwnerIfAbsent(owner);a.profile().vip="192.168.1.254";a.profile().vrrpPriority=170;
        a.profile().vrrp.port=0;a.profile().vrrp.group=42;a.profile().vrrp.advertisementTicks=60;a.profile().vrrp.preempt=false;a.profile().vrrp.enabled=false;
        a.profile().failed=true;a.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        h.startSequence().thenIdle(10).thenExecute(()->{
            var drops=Block.getDrops(a.getBlockState(),h.getLevel(),a.getBlockPos(),a);h.assertTrue(drops.size()==1,"機器1個の通常ドロップ");
            var b=device(h,DeviceKind.ROUTER,6,2);b.restoreItem(drops.getFirst());var p=b.profile();
            h.assertTrue(p.vip.equals("192.168.1.254")&&p.vrrpPriority==170&&p.vrrp.group==42&&p.vrrp.port==0,"VIP・優先度・VRID・ポートを復元");
            h.assertTrue(p.vrrp.advertisementTicks==60&&!p.vrrp.preempt&&!p.vrrp.enabled,"停止状態・広告間隔・preemptを復元");
            h.assertTrue(p.failed&&p.ports[0].moduleTier==2&&b.modules.getItem(0).getCount()==1,"故障とモジュールを維持、複製しない");
            h.assertTrue(b.canUse(owner)&&!b.canUse(guest),"持ち運び後も所有権を検査");
            h.assertTrue(!NetworkWorld.get(h.getLevel()).simulator().vrrpMaster(b.id()),"Master役割・期限は保存しない");
        }).thenSucceed();
    }
}
