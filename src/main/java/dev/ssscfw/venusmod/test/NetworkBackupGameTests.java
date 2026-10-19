package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 物理配線・実メニュー・通常ドロップによるバックアップ検査。復元トークンは実際の応答から取得。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkBackupGameTests {
    private static int nextMenu=200;
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,5));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;return d;
    }
    private static FakePlayer player(GameTestHelper h,String name){return new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));}
    private static NetworkMenu menu(NetworkDeviceEntity d,FakePlayer p){
        p.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);
        var m=new NetworkMenu(nextMenu++,p.getInventory(),d);p.containerMenu=m;return m;
    }
    private static void wire(GameTestHelper h,int x){var p=h.absolutePos(new BlockPos(x,2,5));var b=NetworkContent.CABLES.get(CableGrade.CAT5E).get();h.getLevel().setBlock(p,Block.updateFromNeighbourShapes(b.defaultBlockState(),h.getLevel(),p),3);}
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String line){var result=NetworkServiceCommands.run(w.simulator(),d.id(),line);h.assertTrue(result.changed(),line+" "+result.lines());d.adoptProfile(w.simulator().nodes().get(d.id()));d.refreshState();}
    private static String token(GameTestHelper h,NetworkMenu menu){var m=Pattern.compile("backup confirm ([0-9a-f]{8})").matcher(menu.snapshot());h.assertTrue(m.find(),"実際の確認応答にトークン: "+menu.snapshot());return m.group(1);}

    @GameTest(template="test/wide_empty",batch="network_backup_local",timeoutTicks=120)
    public static void offlineRestoreConfirmationAndPackedArchive(GameTestHelper h){
        var d=device(h,DeviceKind.PC,2,"10.121.0.10");d.profile().name="before";
        var owner=player(h,"backup-owner");var guest=player(h,"backup-guest");d.setOwnerIfAbsent(owner);
        NetworkMenu[] opened={null};String[] confirm={null};
        h.startSequence().thenIdle(10).thenExecute(()->{
            opened[0]=menu(d,owner);opened[0].command(owner,"backup save stable");h.assertTrue(d.backups().entries().size()==1,"実メニューからローカル保存");
            d.profile().name="broken";d.profile().failed=true;d.profile().publicService=true;d.refreshState();
            d.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        }).thenIdle(3).thenExecute(()->{
            opened[0].command(owner,"backup restore stable");confirm[0]=token(h,opened[0]);h.assertTrue(d.profile().name.equals("broken"),"確認前に適用しない");
        }).thenIdle(3).thenExecute(()->{
            d.profile().name="concurrent";opened[0].command(owner,"backup confirm "+confirm[0]);h.assertTrue(d.profile().name.equals("concurrent"),"他の編集から古い値で上書きしない");
        }).thenIdle(3).thenExecute(()->{opened[0].command(owner,"backup restore stable");confirm[0]=token(h,opened[0]);})
        .thenIdle(3).thenExecute(()->{
            opened[0].command(owner,"backup confirm "+confirm[0]);h.assertTrue(d.profile().name.equals("before")&&d.profile().failed&&d.profile().publicService,"設定だけ復元して故障・公開状態を保持");
            h.assertTrue(d.profile().ports[0].moduleTier==2&&d.canUse(owner)&&!d.canUse(guest),"現状の装着品と所有権を維持");
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"通常採掘は控え入り機器1個");
            var copy=device(h,DeviceKind.PC,6,"");copy.restoreItem(drops.getFirst());
            h.assertTrue(copy.backups().get("stable").equals(d.backups().get("stable")),"保存控えを通常ドロップから復元");
            h.assertTrue(copy.profile().failed&&copy.profile().ports[0].moduleTier==2&&copy.canUse(owner)&&!copy.canUse(guest),"持ち運びでも故障・装着品・所有権を保持");
            var forbidden=menu(copy,guest);forbidden.command(guest,"backup delete stable");h.assertTrue(copy.backups().entries().size()==1&&!forbidden.snapshot().contains("stable"),"publicだけでは一覧も削除もできない");
        }).thenIdle(3).thenExecute(()->{
            opened[0].command(owner,"backup restore stable");confirm[0]=token(h,opened[0]);owner.containerMenu=owner.inventoryMenu;
        }).thenIdle(3).thenExecute(()->{
            d.profile().name="closed-menu";opened[0].command(owner,"backup confirm "+confirm[0]);h.assertTrue(d.profile().name.equals("closed-menu"),"閉じた画面から復元不可");
        }).thenSucceed();
    }

    @GameTest(template="test/wide_empty",batch="network_backup_remote",timeoutTicks=180)
    public static void remoteArchiveHonorsNatAclRightsAndBrokenCable(GameTestHelper h){
        var c=device(h,DeviceKind.PC,1,"10.122.1.10");c.profile().gateway="10.122.1.1";
        var r=device(h,DeviceKind.ROUTER,5,"");r.profile().dhcp=false;for(var p:r.profile().ports)p.address="";r.profile().ports[3].address="10.122.1.1";r.profile().ports[1].address="10.122.2.1";
        var v=device(h,DeviceKind.SERVER,9,"10.122.2.20");
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);
        var alice=player(h,"backup-alice");var bob=player(h,"backup-bob");c.setOwnerIfAbsent(alice);v.setOwnerIfAbsent(bob);v.trust(alice.getUUID(),true);
        var w=NetworkWorld.get(h.getLevel());NetworkMenu[] source={null};long[] expiry={0};
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready(),"実配線解析");config(h,w,r,"nat port 3 inside");config(h,w,r,"nat port 1 outside");config(h,w,r,"nat on");
            menu(v,bob).command(bob,"backup server on");source[0]=menu(c,alice);source[0].command(alice,"backup send 10.122.2.20 first");
            h.assertTrue(v.backups().entries().containsKey("first")&&!w.simulator().nat().sessions(r.id()).isEmpty(),"返信経路をNAPT経由で確立して実設定を送信");
        }).thenIdle(3).thenExecute(()->{
            source[0].command(alice,"backup fetch 10.122.2.20 first fetched");h.assertTrue(c.backups().get("fetched").equals(v.backups().get("first")),"遠隔から手元への取得");
            expiry[0]=w.simulator().nat().sessions(r.id()).getFirst().expiresAt();v.trust(alice.getUUID(),false);v.profile().publicService=true;
        }).thenIdle(3).thenExecute(()->{
            source[0].command(alice,"backup send 10.122.2.20 denied");h.assertTrue(!v.backups().entries().containsKey("denied"),"共有解除後はpublicでも保存不可");
            h.assertTrue(w.simulator().nat().sessions(r.id()).getFirst().expiresAt()==expiry[0],"権限拒否でNAT期限を延長しない");
            v.trust(alice.getUUID(),true);config(h,w,r,"acl add 3 in 10 deny tcp any any 5001");config(h,w,r,"acl add 3 in 20 permit ip any any");
        }).thenIdle(3).thenExecute(()->{
            source[0].command(alice,"backup send 10.122.2.20 acl_denied");h.assertTrue(!v.backups().entries().containsKey("acl_denied")&&w.simulator().nat().sessions(r.id()).isEmpty(),"実ACL拒否で保管・変換表は未確定");
            config(h,w,r,"acl clear 3 in");h.getLevel().setBlock(h.absolutePos(new BlockPos(7,2,5)),Blocks.AIR.defaultBlockState(),3);
        }).thenIdle(3).thenExecute(()->{
            source[0].command(alice,"backup send 10.122.2.20 cut");h.assertTrue(!v.backups().entries().containsKey("cut"),"実断線中は保存成功にしない");wire(h,7);
        }).thenIdle(12).thenExecute(()->{
            source[0].command(alice,"backup send 10.122.2.20 repaired");h.assertTrue(v.backups().entries().containsKey("repaired"),"配線復旧で遠隔保管");
            var drops=Block.getDrops(v.getBlockState(),h.getLevel(),v.getBlockPos(),v);var copy=device(h,DeviceKind.SERVER,13,"");copy.restoreItem(drops.getFirst());
            h.assertTrue(copy.backups().serving()&&copy.backups().entries().equals(v.backups().entries()),"Serverの受信設定と他機器の控えを通常ドロップに保存");
        }).thenSucceed();
    }

    @GameTest(template="test/wide_empty",batch="network_backup_corrupt",timeoutTicks=100)
    public static void corruptedArchiveIsQuarantinedWithoutLosingMachineSettings(GameTestHelper h){
        var d=device(h,DeviceKind.PC,2,"10.123.0.10");var owner=player(h,"archive-owner");d.setOwnerIfAbsent(owner);d.profile().name="preserved";
        h.startSequence().thenIdle(10).thenExecute(()->{
            var item=d.savedItem();var data=item.get(DataComponents.CUSTOM_DATA).copyTag();data.getCompound("venus_network").putByteArray("BackupArchive",new byte[]{1,2,3});item.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            var copy=device(h,DeviceKind.PC,6,"");copy.restoreItem(item);boolean rejected=false;try{copy.backups();}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected&&copy.profile().name.equals("preserved")&&copy.canUse(owner),"保管庫だけ隔離し通常設定は維持");
            var again=copy.savedItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("venus_network").getByteArray("BackupArchive");h.assertTrue(Arrays.equals(again,new byte[]{1,2,3}),"保存し直しても破損した原データを黙って消さない");
            menu(copy,owner).command(owner,"backup discard-corrupt confirm");h.assertTrue(copy.backups().entries().isEmpty()&&copy.profile().name.equals("preserved"),"明示操作で隔離データだけ破棄");
        }).thenSucceed();
    }
}
