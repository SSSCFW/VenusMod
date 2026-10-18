package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

/** 実メニューの認可と通常配線・ItemStackを検査。クライアントWidgetの目視試験とは別。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkFormGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static ServerPlayer player(GameTestHelper h,NetworkDeviceEntity d,String name){
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
        p.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);return p;
    }
    private static NetworkMenu menu(ServerPlayer p,NetworkDeviceEntity d){var m=new NetworkMenu(33,p.getInventory(),d);p.containerMenu=m;return m;}
    private static NetworkForm.Values fields(NetworkForm.View view,String name,String ip,String gw,String dns){
        var v=view.values();return new NetworkForm.Values(v.scope(),name,ip,v.prefix(),gw,dns,v.enabled(),v.trunk(),v.optical(),v.vlan(),v.speed());
    }
    private static void wire(GameTestHelper h,int x,int z){var pos=h.absolutePos(new BlockPos(x,2,z));var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(state,h.getLevel(),pos),3);}

    @GameTest(template="test/wide_empty",batch="network_form_config",timeoutTicks=100)
    public static void formsConfigureRealWiringWithoutPartialWrites(GameTestHelper h){
        var a=device(h,DeviceKind.PC,1,5);var b=device(h,DeviceKind.PC,9,5);device(h,DeviceKind.L2_SWITCH,5,5);
        for(int x=2;x<5;x++)wire(h,x,5);for(int x=6;x<9;x++)wire(h,x,5);
        var ownerA=player(h,a,"form-a");var ownerB=player(h,b,"form-b");a.setOwnerIfAbsent(ownerA);b.setOwnerIfAbsent(ownerB);
        var menuA=menu(ownerA,a);var menuB=menu(ownerB,b);var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"通常イベントで配線が解決する");
            var va=NetworkForm.capture(a.profile(),-1);var vb=NetworkForm.capture(b.profile(),-1);
            var ra=menuA.editForm(ownerA,NetworkForm.encode(va),NetworkForm.encode(fields(va,"フォームA","10.88.0.10","","")));
            var rb=menuB.editForm(ownerB,NetworkForm.encode(vb),NetworkForm.encode(fields(vb,"フォームB","10.88.0.20","","")));
            h.assertTrue(ra.accepted()&&rb.accepted(),"両端の実メニュー経由で設定を保存する: "+ra.message()+" / "+rb.message());
            h.assertTrue(world.simulator().ping(a.id(),"10.88.0.20").success(),"フォーム設定から通常配線のpingが成功");
            h.assertTrue(world.simulator().startTransfer(a.id(),"10.88.0.20",1_000_000_000L).success(),"仮想転送を開始");
        }).thenIdle(3).thenExecute(()->{
            var v=NetworkForm.capture(a.profile(),-1);String before=a.profile().encode();
            var result=menuA.editForm(ownerA,NetworkForm.encode(v),NetworkForm.encode(fields(v,"壊してはいけない","999.0.0.1","","")));
            h.assertTrue(!result.accepted()&&a.profile().encode().equals(before),"不正IPで名前だけ書き換えない");
            h.assertTrue(world.simulator().flows().stream().anyMatch(f->f.source.equals(a.id())),"拒否した編集で転送を消去しない");
        }).thenIdle(3).thenExecute(()->{
            var v=NetworkForm.capture(a.profile(),-1);var result=menuA.editForm(ownerA,NetworkForm.encode(v),NetworkForm.encode(fields(v,"表示名の変更","10.88.0.10","","")));
            h.assertTrue(result.accepted()&&world.simulator().flows().stream().anyMatch(f->f.source.equals(a.id())),"名前だけの変更は通信を維持");
            h.assertTrue(a.profile().name.equals("表示名の変更"),"BlockEntityへ更新が反映される");
        }).thenSucceed();
    }

    @GameTest(template="test/wide_empty",batch="network_form_access",timeoutTicks=100)
    public static void permissionsConflictHardwareAndDrop(GameTestHelper h){
        var d=device(h,DeviceKind.ROUTER,2,2);var owner=player(h,d,"form-owner");var guest=player(h,d,"form-guest");d.setOwnerIfAbsent(owner);
        var m=menu(owner,d);var guestMenu=menu(guest,d);d.profile().publicService=true;d.profile().failed=true;
        d.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        h.startSequence().thenIdle(15).thenExecute(()->{
            var v=NetworkForm.capture(d.profile(),-1);String expected=NetworkForm.encode(v),edit=NetworkForm.encode(fields(v,"許可なし","","","")),before=d.profile().encode();
            h.assertTrue(!guestMenu.editForm(guest,expected,edit).accepted(),"サービス公開は設定の許可ではない");
            owner.setPos(d.getBlockPos().getX()+30,d.getBlockPos().getY(),d.getBlockPos().getZ());
            h.assertTrue(!m.editForm(owner,expected,edit).accepted(),"遠距離からの書き込みを拒否");
            owner.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);owner.containerMenu=owner.inventoryMenu;
            h.assertTrue(!m.editForm(owner,expected,edit).accepted(),"閉じたメニューへの遅延パケットを拒否");owner.containerMenu=m;
            h.assertTrue(before.equals(d.profile().encode()),"拒否した操作は永続設定を変更しない");
            var old=NetworkForm.capture(d.profile(),0);d.profile().ports[0].vlan=23;
            h.assertTrue(!m.editForm(owner,NetworkForm.encode(old),NetworkForm.encode(old.values())).accepted(),"他の編集後は古いポート画面で上書きしない");
        }).thenIdle(3).thenExecute(()->{
            var old=NetworkForm.capture(d.profile(),0);d.modules.setItem(0,ItemStack.EMPTY);
            var v=old.values();var high=new NetworkForm.Values(0,"",v.address(),v.prefix(),"","",true,false,false,"23","40000000000");
            h.assertTrue(!m.editForm(owner,NetworkForm.encode(old),NetworkForm.encode(high)).accepted(),"フォーム表示中のモジュール抜去を検知");
            d.modules.setItem(0,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        }).thenIdle(3).thenExecute(()->{
            var v=NetworkForm.capture(d.profile(),-1);var result=m.editForm(owner,NetworkForm.encode(v),NetworkForm.encode(fields(v,"保存する名前",v.values().address(),v.values().gateway(),"10.99.0.53")));
            h.assertTrue(result.accepted(),"最新設定で正常な変更を受理");
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"機器1個に保存");
            var copy=device(h,DeviceKind.ROUTER,6,2);copy.restoreItem(drops.getFirst());
            h.assertTrue(copy.profile().name.equals("保存する名前")&&copy.profile().dns.equals("10.99.0.53")&&copy.profile().ports[0].vlan==23,"フォームと他の設定をドロップから復元");
            h.assertTrue(copy.profile().failed&&copy.profile().ports[0].moduleTier==2&&copy.canUse(owner)&&!copy.canUse(guest),"故障・装着品・所有権も維持");
        }).thenSucceed();
    }

    @GameTest(template="test/empty",batch="network_form_codec")
    public static void boundedFormPayloadRoundTrip(GameTestHelper h){
        var profile=new NetworkProfile(DeviceKind.PC);var view=NetworkForm.capture(profile,-1);
        var edit=new NetworkPackets.FormEdit(33,7,NetworkForm.encode(view),NetworkForm.encode(view.values()));
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{NetworkPackets.FormEdit.CODEC.encode(buffer,edit);h.assertTrue(NetworkPackets.FormEdit.CODEC.decode(buffer).equals(edit),"編集Payloadを往復");}finally{buffer.release();}
        var state=new NetworkPackets.FormState(33,7,true,"保存",NetworkForm.captureAll(profile),Collections.nCopies(6,"未接続"));
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{NetworkPackets.FormState.CODEC.encode(buffer,state);h.assertTrue(NetworkPackets.FormState.CODEC.decode(buffer).equals(state),"表示Payloadを往復");}finally{buffer.release();}
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());boolean refused=false;
        try{buffer.writeVarInt(33);buffer.writeVarInt(7);buffer.writeUtf("x".repeat(NetworkForm.MAX_ENCODED+1));buffer.writeUtf("");try{NetworkPackets.FormEdit.CODEC.decode(buffer);}catch(RuntimeException expected){refused=true;}}
        finally{buffer.release();}
        h.assertTrue(refused,"UTFサイズをデコード段階で制限");h.succeed();
    }
}
