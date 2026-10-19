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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 実ケーブル・通常tick・実メニュー/機器ドロップ。同期時刻・論理リンクは注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkNtpGameTests {
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
        d.adoptProfile(w.simulator().nodes().get(d.id()));if(command.startsWith("ntp")||command.startsWith("syslog"))d.setChanged();else d.refreshState();
    }
    @GameTest(template="test/wide_empty",batch="network_ntp",timeoutTicks=210)
    public static void scheduledClockNatAclAndPhysicalCut(GameTestHelper h){
        var a=device(h,DeviceKind.PC,1,5,"10.121.1.10");a.profile().gateway="10.121.1.1";
        var r=device(h,DeviceKind.ROUTER,5,5,"");r.profile().dhcp=false;for(var p:r.profile().ports)p.address="";r.profile().ports[3].address="10.121.1.1";r.profile().ports[1].address="10.121.2.1";
        var b=device(h,DeviceKind.SERVER,9,5,"10.121.2.20");
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);var w=NetworkWorld.get(h.getLevel());
        long[] count={0};double[] correction={0};
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready(),"実配線解析完了");config(h,w,r,"nat port 3 inside");config(h,w,r,"nat port 1 outside");config(h,w,r,"nat on");
            config(h,w,b,"ntp server on");config(h,w,a,"ntp offset 5000");config(h,w,a,"ntp target 10.121.2.20");config(h,w,a,"ntp interval 1");config(h,w,a,"ntp on");
            h.assertTrue(w.simulator().ntp().snapshot(a.id()).samples()==0,"開始だけで同期成功にしない");
        }).thenIdle(5).thenExecute(()->{
            var time=w.simulator().ntp();h.assertTrue(time.snapshot(a.id()).state().equals("SYNCED"),"実配線とNAPTでNTP同期");
            h.assertTrue(Math.abs(time.clockMillis(a.id())-time.clockMillis(b.id()))<0.02,"2機器の表示時計が合う");
            config(h,w,b,"syslog server on");config(h,w,a,"syslog target 10.121.2.20");config(h,w,a,"syslog on");w.simulator().log(a.id(),3,"ntp-native-time");
        }).thenIdle(5).thenExecute(()->{
            var e=w.simulator().syslog().entries(b.id()).stream().filter(x->x.message().equals("ntp-native-time")).findFirst().orElseThrow();
            h.assertTrue(Math.abs(e.emittedClockMillis()-e.emittedTick()*50.0)<0.02,"同期済み表示時計を発生時に保存");
            count[0]=w.simulator().ntp().snapshot(a.id()).samples();correction[0]=w.simulator().ntp().snapshot(a.id()).correctionMillis();
            config(h,w,r,"acl add 3 in 10 deny udp any any 123");config(h,w,r,"acl add 3 in 20 permit ip any any");
        }).thenIdle(25).thenExecute(()->{
            var state=w.simulator().ntp().snapshot(a.id());h.assertTrue(state.samples()==count[0]&&state.detail().contains("ACL"),"UDP123拒否では同期しない");
            h.assertTrue(state.correctionMillis()==correction[0],"失敗した同期で時計を変えない");config(h,w,r,"acl clear 3 in");
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(w.simulator().ntp().snapshot(a.id()).state().equals("SYNCED"),"ACL解除で再同期");
            h.getLevel().setBlock(h.absolutePos(new BlockPos(7,2,5)),Blocks.AIR.defaultBlockState(),3);
        }).thenIdle(70).thenExecute(()->{
            h.assertTrue(w.simulator().ntp().snapshot(a.id()).state().equals("STALE"),"断線後の古い同期を新鮮と表示しない");wire(h,7);
        }).thenIdle(25).thenExecute(()->h.assertTrue(w.simulator().ntp().snapshot(a.id()).state().equals("SYNCED"),"実ケーブル復旧で再同期")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_ntp_persistence",timeoutTicks=100)
    public static void ntpMenuOwnershipAndItemRoundTrip(GameTestHelper h){
        var d=device(h,DeviceKind.SERVER,2,2,"10.122.0.20");var w=NetworkWorld.get(h.getLevel());
        var owner=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"ntp-owner"));var guest=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"ntp-guest"));
        owner.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);guest.setPos(owner.getX(),owner.getY(),owner.getZ());d.setOwnerIfAbsent(owner);
        h.startSequence().thenIdle(15).thenExecute(()->{
            config(h,w,d,"ntp target 10.122.0.30 8123");config(h,w,d,"ntp offset -3456");config(h,w,d,"ntp drift 50");config(h,w,d,"ntp interval 7");
            var menu=new NetworkMenu(62,owner.getInventory(),d);owner.containerMenu=menu;menu.command(owner,"ntp on");h.assertTrue(d.profile().ntp.enabled,"実メニューで同期有効化");
            d.profile().publicService=true;var blocked=new NetworkMenu(63,guest.getInventory(),d);guest.containerMenu=blocked;blocked.command(guest,"ntp off");
            h.assertTrue(d.profile().ntp.enabled&&!blocked.snapshot().contains("8123"),"publicはNTP管理画面を公開しない");
            owner.containerMenu=owner.inventoryMenu;menu.command(owner,"ntp off");h.assertTrue(d.profile().ntp.enabled,"閉じたメニューから旧命令を受けない");
            d.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));d.profile().failed=true;
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"通常ドロップに保存");
            var restored=device(h,DeviceKind.SERVER,6,2,"");restored.restoreItem(drops.getFirst());var p=restored.profile();
            h.assertTrue(p.ntp.config().equals(d.profile().ntp.config()),"NTP全設定を持ち運べる");
            h.assertTrue(p.failed&&p.ports[0].moduleTier==2&&restored.canUse(owner)&&!restored.canUse(guest),"故障・装着品・所有権を保持");
            var state=w.simulator().ntp().snapshot(restored.id());h.assertTrue(state.samples()==0&&state.correctionMillis()==0&&state.lastSuccess()<0,"同期結果や補正を複製しない");
        }).thenSucceed();
    }
}
