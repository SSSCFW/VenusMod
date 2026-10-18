package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

/** 論理リンクやBGP経路を注入せず、通常ブロック配線と公開CLIで検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkBgpGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void wire(GameTestHelper h,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(state,h.getLevel(),pos),3);
    }
    private static void command(GameTestHelper h,NetworkDeviceEntity device,String text){
        var world=NetworkWorld.get(h.getLevel());var reply=NetworkServiceCommands.run(world.simulator(),device.id(),text);
        h.assertTrue(reply.handled()&&reply.changed(),text+" / "+reply.lines());device.adoptProfile(world.simulator().nodes().get(device.id()));device.refreshState();
    }
    private static void configure(GameTestHelper h,NetworkDeviceEntity d,long asn,String rid,String ip,long remoteAs,String lan){
        command(h,d,"bgp as "+asn);command(h,d,"bgp router-id "+rid);command(h,d,"bgp neighbor "+ip+" remote-as "+remoteAs);
        command(h,d,"bgp neighbor "+ip+" import all");command(h,d,"bgp neighbor "+ip+" export all");command(h,d,"bgp network add "+lan);command(h,d,"bgp on");
    }
    @GameTest(template="test/wide_empty",batch="network_bgp",timeoutTicks=850)
    public static void bgpWiringWithdrawAndRecovery(GameTestHelper h){
        var a=device(h,DeviceKind.ROUTER,2,4);var b=device(h,DeviceKind.ROUTER,6,4);
        var x=device(h,DeviceKind.PC,2,0);var y=device(h,DeviceKind.PC,6,0);
        for(var d:List.of(a,b)){d.profile().dhcp=false;for(var p:d.profile().ports)p.address="";}
        a.profile().ports[1].address="203.0.113.1";b.profile().ports[3].address="203.0.113.2";
        a.profile().ports[0].address="10.31.0.1";b.profile().ports[0].address="10.32.0.1";
        x.profile().address="10.31.0.10";x.profile().gateway="10.31.0.1";y.profile().address="10.32.0.10";y.profile().gateway="10.32.0.1";
        for(int z=1;z<4;z++){wire(h,2,z);wire(h,6,z);}for(int xx=3;xx<6;xx++)wire(h,xx,4);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"通常イベントで配線を索引化");
            h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"静的経路を注入していない");
            configure(h,a,65031,"31.31.31.31","203.0.113.2",65032,"10.31.0.0/24");
            configure(h,b,65032,"32.32.32.32","203.0.113.1",65031,"10.32.0.0/24");
            h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"OPEN/広告同期前は経路なし");
        }).thenIdle(140).thenExecute(()->{
            var sim=world.simulator();h.assertTrue(sim.ping(x.id(),y.profile().address).success(),"実配線のeBGP経路で往復");
            h.assertTrue(sim.bgp().neighbors(a.id()).getFirst().state()==BgpEngine.State.ESTABLISHED,"相互OPEN/KEEPALIVEで確立");
            h.assertTrue(a.profile().routes.isEmpty(),"自動学習が静的設定を書き換えない");
            command(h,b,"bgp network delete 10.32.0.0/24");
        }).thenIdle(100).thenExecute(()->{
            h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"UPDATE撤回で到達不能");
            command(h,b,"bgp network add 10.32.0.0/24");
        }).thenIdle(140).thenExecute(()->{
            h.assertTrue(world.simulator().ping(x.id(),y.profile().address).success(),"再広告で復旧");
            b.profile().failed=true;b.refreshState();
        }).thenIdle(100).thenExecute(()->{
            h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"機器故障で停止");
            h.assertTrue(world.simulator().bgp().routes(a.id()).stream().noneMatch(r->r.prefix().toString().equals("10.32.0.0/24")),"広告停止からHold満了で学習経路撤回");
            b.repair();
        }).thenIdle(140).thenExecute(()->h.assertTrue(world.simulator().ping(x.id(),y.profile().address).success(),"修理後のセッション再形成と経路復旧")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_bgp",timeoutTicks=100)
    public static void bgpSettingsSurviveDeviceDrop(GameTestHelper h){
        var a=device(h,DeviceKind.ROUTER,2,9);
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"bgp-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"bgp-guest"),ClientInformation.createDefault());
        a.setOwnerIfAbsent(owner);a.profile().bgp.asn=4200000001L;a.profile().bgp.routerId="7.7.7.7";a.profile().bgp.enabled=true;
        a.profile().bgp.keepaliveTicks=40;a.profile().bgp.holdTicks=160;
        var peer=new BgpSettings.Peer("192.168.1.2",4200000002L,0,true,3,250,80,new BgpSettings.Policy("10.0.0.0/8"),new BgpSettings.Policy("all"));
        a.profile().bgp.peers.put(peer.address(),peer);a.profile().bgp.networks.add(NetworkPolicy.Prefix.parse("192.168.2.0/24"));
        a.profile().failed=true;a.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        var drops=Block.getDrops(a.getBlockState(),h.getLevel(),a.getBlockPos(),a);h.assertTrue(drops.size()==1,"設定付き機器が1個だけ落ちる");
        var b=device(h,DeviceKind.ROUTER,5,9);b.restoreItem(drops.getFirst());
        h.assertTrue(b.profile().bgp.asn==4200000001L&&b.profile().bgp.routerId.equals("7.7.7.7")&&b.profile().bgp.enabled,"4byte AS・ID・有効状態保持");
        h.assertTrue(b.profile().bgp.peers.get(peer.address()).equals(peer)&&b.profile().bgp.networks.equals(a.profile().bgp.networks),"ピア設定・政策・広告prefix保持");
        h.assertTrue(b.profile().bgp.keepaliveTicks==40&&b.profile().bgp.holdTicks==160,"タイマー設定保持");
        h.assertTrue(b.profile().failed&&b.profile().ports[1].moduleTier==2,"故障・ポート強化保持");
        h.assertTrue(b.canUse(owner)&&!b.canUse(guest),"採掘後も所有権を維持");
        h.assertTrue(NetworkWorld.get(h.getLevel()).simulator().bgp().routes(b.id()).isEmpty(),"動的BGP経路はアイテムに保存しない");h.succeed();
    }
}
