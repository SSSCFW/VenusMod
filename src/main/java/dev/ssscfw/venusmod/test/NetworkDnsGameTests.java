package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.neoforged.neoforge.gametest.*;

/** 論理リンク・キャッシュを注入せず、通常配線とサーバーtickでDNSを検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkDnsGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var result=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);result.profile().address=ip;return result;
    }
    private static void wire(GameTestHelper h,int x,int z){var pos=h.absolutePos(new BlockPos(x,2,z));var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(state,h.getLevel(),pos),3);}
    private static void config(GameTestHelper h,NetworkWorld world,NetworkDeviceEntity device,String command){
        var r=NetworkServiceCommands.run(world.simulator(),device.id(),command);h.assertTrue(r.handled()&&r.changed(),command+" / "+r.lines());
        device.adoptProfile(world.simulator().nodes().get(device.id()));if(command.startsWith("dns-"))device.setChanged();else device.refreshState();
    }
    @GameTest(template="test/wide_empty",batch="network_dns",timeoutTicks=150)
    public static void actualWiringCacheTtlAndRoundRobin(GameTestHelper h){
        var client=device(h,DeviceKind.PC,1,5,"10.44.0.10");client.profile().dns="10.44.0.53";
        device(h,DeviceKind.L2_SWITCH,5,5,"");var dns=device(h,DeviceKind.SERVER,5,1,"10.44.0.53");
        var a=device(h,DeviceKind.SERVER,9,5,"10.44.0.20");var b=device(h,DeviceKind.SERVER,5,9,"10.44.0.21");a.profile().web=b.profile().web=true;
        for(int x=2;x<5;x++)wire(h,x,5);for(int x=6;x<9;x++)wire(h,x,5);
        for(int z=2;z<5;z++)wire(h,5,z);for(int z=6;z<9;z++)wire(h,5,z);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"通常イベントで配線を解決: "+world.diagnostic());var sim=world.simulator();
            config(h,world,dns,"dns-record set plant.local 1 10.44.0.20");
            var result=sim.ping(client.id(),"PLANT.local.");h.assertTrue(result.success()&&result.destination().equals(a.id()),"名前による実配線のping");
            config(h,world,dns,"dns-record set plant.local 1 10.44.0.21");
            result=sim.ping(client.id(),"plant.local");h.assertTrue(result.success()&&result.destination().equals(a.id()),"権威側変更直後は古い回答をTTLまで保持");
        }).thenIdle(22).thenExecute(()->{
            var sim=world.simulator();var result=sim.ping(client.id(),"plant.local");h.assertTrue(result.success()&&result.destination().equals(b.id()),"通常サーバーtickでTTL満了後に新しいIP");
            config(h,world,dns,"dns-server off");h.assertTrue(!sim.resolve(client.id(),"plant.local").success(),"直接診断はDNS停止を検出");
            h.assertTrue(sim.ping(client.id(),"plant.local").success(),"キャッシュ中はDNS停止中も名前の利用が可能");
            config(h,world,dns,"dns-server on");config(h,world,dns,"dns-record set pool.local 0 10.44.0.20 10.44.0.21");
            var first=sim.resolve(client.id(),"pool.local");var next=sim.resolve(client.id(),"pool.local");
            h.assertTrue(first.success()&&next.success()&&!first.destination().equals(next.destination()),"通常問い合わせによる回答順の回転");
            h.assertTrue(sim.startTransfer(client.id(),"plant.local:5000",1_000_000_000).success(),"名前による仮想転送開始");
            config(h,world,dns,"dns-record ttl pool.local 10");h.assertTrue(sim.flows().stream().anyMatch(f->f.source.equals(client.id())),"DNSだけの編集で既存転送を切らない");
            h.getLevel().setBlock(h.absolutePos(new BlockPos(5,2,8)),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(sim.dnsResolver().lookup(client.id(),"plant.local").cached(),"DNSキャッシュ自体はリンク切断を検知する装置ではない");
            h.assertTrue(!sim.ping(client.id(),"plant.local").success(),"名前を解決できても実ケーブル切断を迂回しない");
            wire(h,5,8);
        }).thenIdle(15).thenExecute(()->h.assertTrue(world.simulator().ping(client.id(),"plant.local").success(),"再配線で復旧")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_dns",timeoutTicks=100)
    public static void dnsRecordsTtlAndOwnerSurviveDrop(GameTestHelper h){
        var a=device(h,DeviceKind.SERVER,2,2,"10.46.0.53");
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"dns-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"dns-guest"),ClientInformation.createDefault());
        a.setOwnerIfAbsent(owner);a.profile().dnsSettings.set(a.profile(),"plant.local",42,List.of("10.46.0.20","10.46.0.21"));
        a.profile().dnsSettings.enabled=false;a.profile().dnsSettings.negativeTtl=13;a.profile().failed=true;
        a.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        h.startSequence().thenIdle(10).thenExecute(()->{
            var drops=Block.getDrops(a.getBlockState(),h.getLevel(),a.getBlockPos(),a);h.assertTrue(drops.size()==1,"機器1個に保存");
            var b=device(h,DeviceKind.SERVER,5,2,"");b.restoreItem(drops.getFirst());var records=b.profile().dnsSettings.records(b.profile(),"plant.local");
            h.assertTrue(records.ttl()==42&&records.addresses().equals(List.of("10.46.0.20","10.46.0.21")),"複数AレコードとTTLを保存復元");
            h.assertTrue(!b.profile().dnsSettings.enabled&&b.profile().dnsSettings.negativeTtl==13,"サーバー状態と否定TTLを保持");
            h.assertTrue(b.profile().failed&&b.profile().ports[0].moduleTier==2,"故障と装着品を保持");
            h.assertTrue(b.canUse(owner)&&!b.canUse(guest)&&!b.canControl(guest),"名前登録やアイテム復元で他人へ操作権限を付与しない");
            h.assertTrue(NetworkWorld.get(h.getLevel()).simulator().dnsResolver().describe(b.id()).getFirst().contains("空"),"キャッシュは持ち運ばない");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_dns",timeoutTicks=100)
    public static void actualWiringDnsAclAndServiceSeparation(GameTestHelper h){
        var client=device(h,DeviceKind.PC,1,5,"10.45.1.10");client.profile().gateway="10.45.1.1";client.profile().dns="10.45.2.53";
        var router=device(h,DeviceKind.ROUTER,5,5,"");router.profile().dhcp=false;for(var p:router.profile().ports)p.address="";
        router.profile().ports[3].address="10.45.1.1";router.profile().ports[1].address="10.45.2.1";
        var server=device(h,DeviceKind.SERVER,9,5,"10.45.2.53");server.profile().gateway="10.45.2.1";server.profile().web=true;
        for(int x=2;x<5;x++)wire(h,x,5);for(int x=6;x<9;x++)wire(h,x,5);var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            var sim=world.simulator();config(h,world,server,"dns-record base.local 10.45.2.53");
            config(h,world,router,"acl add 3 in 10 deny udp any any 53");config(h,world,router,"acl add 3 in 20 permit ip any any");
            var answer=sim.dnsResolver().lookup(client.id(),"base.local");h.assertTrue(!answer.success()&&!answer.negative()&&answer.message().contains("ACL"),"UDP53遮断を名前なしと混同しない");
            config(h,world,router,"acl clear 3 in");answer=sim.dnsResolver().lookup(client.id(),"base.local");h.assertTrue(answer.success()&&!answer.cached(),"ACL解除後は否定キャッシュ待ちなし");
            config(h,world,router,"acl add 3 in 10 deny udp any any 53");config(h,world,router,"acl add 3 in 20 permit ip any any");
            var endpoint=sim.resolveEndpoint(client.id(),"base.local:80",80);
            h.assertTrue(sim.requestService(client.id(),endpoint.address(),"tcp",endpoint.port(),80).success(),"キャッシュ回答でUDP53遮断中も許可されたWebへ接続");
            config(h,world,router,"acl add 3 in 5 deny tcp any any");
            h.assertTrue(!sim.requestService(client.id(),endpoint.address(),"tcp",endpoint.port(),80).success(),"DNS回答はアプリ側ACLを迂回しない");
        }).thenSucceed();
    }
}
