package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import dev.ssscfw.venusmod.registry.ModCreativeTabs;
import java.util.HashSet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.*;

/** 実BlockState/ItemStackで形状と持ち運びを検証する。描画の目視試験とは別。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkInfrastructureGameTests {
    @GameTest(template="test/empty")
    public static void thinCableShapes(GameTestHelper h) {
        for(var grade:CableGrade.values()) {
            var block=NetworkContent.CABLES.get(grade).get();
            for(var state:block.getStateDefinition().getPossibleStates()) {
                h.assertTrue(state.getOcclusionShape(h.getLevel(),BlockPos.ZERO).isEmpty(),"細いケーブルは隣の面を隠さない");
                var bounds=state.getShape(h.getLevel(),BlockPos.ZERO,CollisionContext.empty()).bounds();
                h.assertTrue(bounds.minX>=0&&bounds.maxX<=1&&bounds.minY>=0&&bounds.maxY<=1&&bounds.minZ>=0&&bounds.maxZ<=1,"形状がブロック内に収まる");
            }
            var bounds=block.defaultBlockState().getShape(h.getLevel(),BlockPos.ZERO).bounds();
            h.assertTrue(Math.abs(bounds.getXsize()-0.25)<1e-8&&Math.abs(bounds.getYsize()-0.25)<1e-8&&Math.abs(bounds.getZsize()-0.25)<1e-8,"未接続の芯は4/16幅");
        }
        h.succeed();
    }
    @GameTest(template="test/empty")
    public static void cableCreativeTabRegistration(GameTestHelper h) {
        h.assertTrue(!ModCreativeTabs.NETWORK_CABLES_TAB_KEY.equals(ModCreativeTabs.VENUS_TAB_KEY),
                "ケーブル用タブは金星メインタブとは異なるIDを使う");
        h.assertTrue(ModCreativeTabs.NETWORK_CABLES_TAB.get() != ModCreativeTabs.VENUS_TAB.get(),
                "ネットワークケーブル専用タブが登録される");
        var unique=new HashSet<Item>();
        for(var grade:CableGrade.values()){
            Item plain=NetworkContent.NETWORK_ITEMS.get(grade.id()).get();
            h.assertTrue(plain instanceof NetworkCableItem,"無染色ケーブルも専用タブの対象: "+grade.id());
            unique.add(plain);
            for(var color:DyeColor.values()){
                Item dyed=NetworkContent.NETWORK_ITEMS.get(grade.id()+"_"+color.getName()).get();
                h.assertTrue(dyed instanceof NetworkCableItem,"全16色がケーブルアイテム: "+grade.id()+"/"+color.getName());
                unique.add(dyed);
            }
        }
        h.assertTrue(unique.size()==CableGrade.values().length*(DyeColor.values().length+1),
                "8規格×(無染色+16色)の136アイテムが重複なく登録される");
        h.succeed();
    }

    @GameTest(template="test/empty")
    public static void deviceItemRoundTrip(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(1,1,1));
        var state=NetworkContent.DEVICES.get(DeviceKind.ROUTER).get().defaultBlockState();
        h.getLevel().setBlock(p,state,3);
        var first=(NetworkDeviceEntity)h.getLevel().getBlockEntity(p);
        NetworkWorld.get(h.getLevel()).add(first);
        first.profile().name="持ち運びルーター";first.profile().failed=true;
        first.profile().ports[2].vlan=23;first.profile().ports[2].speedLimitBps=10_000_000_000L;
        first.modules.setItem(2,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        first.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
        var drops=net.minecraft.world.level.block.Block.getDrops(state,h.getLevel(),p,first);
        h.assertTrue(drops.size()==1,"通常のブロックドロップは機器1個だけ");
        ItemStack packed=drops.getFirst();
        var copy=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)packed.save(h.getLevel().registryAccess()));
        BlockPos q=h.absolutePos(new BlockPos(2,1,1));h.getLevel().setBlock(q,state,3);
        var restored=(NetworkDeviceEntity)h.getLevel().getBlockEntity(q);restored.restoreItem(copy);
        h.assertTrue(restored.profile().name.equals("持ち運びルーター")&&restored.profile().failed,"名前・故障を復元");
        h.assertTrue(restored.profile().ports[2].vlan==23&&restored.profile().ports[2].moduleTier==2,"ポート設定とモジュールを復元");
        h.assertTrue(restored.profile().stability==3&&restored.modules.getItem(6).getCount()==1,"安定化モジュールを1個だけ復元");
        h.assertTrue(first.modules.getItem(2).getCount()==1,"保存は元の在庫を増減しない");
        h.assertTrue(!first.id().equals(restored.id()),"同時に存在する複製機器のIDを区別");
        h.succeed();
    }
}
