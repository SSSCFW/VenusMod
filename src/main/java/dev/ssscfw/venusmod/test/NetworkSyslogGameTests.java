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

/** 通常のブロック配線とサーバーtick。配送リンク・受信履歴は直接注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkSyslogGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;return d;
    }
    private static void wire(GameTestHelper h,int x){var pos=h.absolutePos(new BlockPos(x,2,5));var block=NetworkContent.CABLES.get(CableGrade.CAT5E).get();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(block.defaultBlockState(),h.getLevel(),pos),3);}
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String input){
        var r=NetworkServiceCommands.run(w.simulator(),d.id(),input);h.assertTrue(r.handled()&&r.changed(),input+" / "+r.lines());d.adoptProfile(w.simulator().nodes().get(d.id()));if(input.startsWith("syslog"))d.setChanged();else d.refreshState();
    }
    private static boolean has(NetworkWorld w,NetworkDeviceEntity d,String text){return w.simulator().syslog().entries(d.id()).stream().anyMatch(e->e.message().contains(text));}
    @GameTest(template="test/wide_empty",batch="network_syslog",timeoutTicks=150)
    public static void oneWayNatAclAndRealDisconnect(GameTestHelper h){
        var a=device(h,DeviceKind.PC,1,5,"10.94.1.10");a.profile().gateway="10.94.1.1";
        var r=device(h,DeviceKind.ROUTER,5,5,"");r.profile().dhcp=false;for(var p:r.profile().ports)p.address="";r.profile().ports[3].address="10.94.1.1";r.profile().ports[1].address="10.94.2.1";
        var c=device(h,DeviceKind.SERVER,9,5,"10.94.2.20");
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);var w=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready(),"通常配線の解析完了");config(h,w,c,"syslog server on");config(h,w,a,"syslog target 10.94.2.20");config(h,w,a,"syslog on");
            h.assertTrue(!w.simulator().ping(a.id(),"10.94.2.20").success(),"collectorに返信経路なし");NetworkCli.run(w.simulator(),a.id(),"syslog test 3 one-way-arrival");
            h.assertTrue(!has(w,c,"one-way-arrival"),"CLI呼出しで即時到着させない");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(has(w,c,"one-way-arrival"),"返信経路なしでも片方向ログ到着");
            config(h,w,r,"nat port 3 inside");config(h,w,r,"nat port 1 outside");config(h,w,r,"nat on");
            h.assertTrue(w.simulator().ping(a.id(),"10.94.2.20").success(),"NAPTでpingも成功する");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(w.simulator().syslog().entries(c.id()).stream().anyMatch(e->e.message().contains("Reply from")&&e.sourceAddress().equals("10.94.2.1")),"実通信の自動ログをNAT越しに収集");
            config(h,w,r,"acl add 3 in 10 deny udp any any 514");config(h,w,r,"acl add 3 in 20 permit ip any any");NetworkCli.run(w.simulator(),a.id(),"syslog test 3 blocked-acl");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(!has(w,c,"blocked-acl"),"ACLで配送を拒否");h.assertTrue(w.simulator().syslog().pending()==0,"配送失敗のログが再帰しない");
            config(h,w,r,"acl clear 3 in");h.getLevel().setBlock(h.absolutePos(new BlockPos(7,2,5)),Blocks.AIR.defaultBlockState(),3);NetworkCli.run(w.simulator(),a.id(),"syslog test 3 cut-cable");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(!has(w,c,"cut-cable"),"実ケーブル断を迂回しない");wire(h,7);
        }).thenIdle(10).thenExecute(()->NetworkCli.run(w.simulator(),a.id(),"syslog test 3 repaired-link")).thenIdle(5).thenExecute(()->{
            h.assertTrue(has(w,c,"repaired-link")&&!has(w,c,"cut-cable"),"復旧後は新規ログのみ受信");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_syslog_persistence",timeoutTicks=100)
    public static void collectorPrivacySettingsAndItemDrop(GameTestHelper h){
        var d=device(h,DeviceKind.SERVER,2,2,"10.95.0.20");var w=NetworkWorld.get(h.getLevel());
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"syslog-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"syslog-guest"),ClientInformation.createDefault());
        owner.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);guest.setPos(owner.getX(),owner.getY(),owner.getZ());d.setOwnerIfAbsent(owner);
        h.startSequence().thenIdle(10).thenExecute(()->{
            config(h,w,d,"syslog server on");config(h,w,d,"syslog target 10.95.0.20");config(h,w,d,"syslog allow 10.95.0.0/24");config(h,w,d,"syslog on");
            NetworkCli.run(w.simulator(),d.id(),"syslog test 3 private-log");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(has(w,d,"private-log"),"自己宛てのログを一度だけ受信");d.profile().publicService=true;
            var forbidden=new NetworkMenu(42,guest.getInventory(),d);guest.containerMenu=forbidden;forbidden.command(guest,"syslog clear");
            h.assertTrue(has(w,d,"private-log")&&!forbidden.snapshot().contains("private-log"),"サービス公開はログ閲覧/消去権限ではない");
            h.assertTrue(d.canUse(owner)&&!d.canUse(guest),"管理権限を分離");config(h,w,d,"syslog target 10.95.0.30 5514");config(h,w,d,"syslog level 4");
            d.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));d.profile().failed=true;
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"機器1個に保存");var restored=device(h,DeviceKind.SERVER,6,2,"");restored.restoreItem(drops.getFirst());
            var p=restored.profile();h.assertTrue(p.syslog.enabled&&p.syslog.server&&p.syslog.target.equals("10.95.0.30")&&p.syslog.port==5514&&p.syslog.level==4&&p.syslog.allow.equals("10.95.0.0/24"),"Syslog設定を保存復元");
            h.assertTrue(p.failed&&p.ports[0].moduleTier==2&&restored.canUse(owner)&&!restored.canUse(guest),"故障・装着品・所有権を保持");
            h.assertTrue(w.simulator().syslog().entries(restored.id()).isEmpty(),"収集済みログを機器複製で増やさない");
        }).thenSucceed();
    }
}
