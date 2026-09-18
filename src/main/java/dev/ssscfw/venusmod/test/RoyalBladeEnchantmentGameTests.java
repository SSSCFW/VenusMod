package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import dev.ssscfw.venusmod.treasure.RoyalBladeEffects;
import dev.ssscfw.venusmod.treasure.RoyalBladeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実際の衝突経路と燃焼・モード同期の試験。実行結果は純Java検査と区別する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoyalBladeEnchantmentGameTests {
    @GameTest(template = "test/empty", timeoutTicks = 40)
    public static void fireAspectBurnsDirectAndBlastTargets(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 2));
        Cow direct = cow(helper, new BlockPos(4, 2, 2));
        Cow nearby = cow(helper, new BlockPos(4, 2, 3));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FIRE_ASPECT), 2);
        RoyalBladeEntity shot = shoot(helper, owner, direct, sword, false);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(shot.isRemoved(), "着弾後に投影を除去");
            helper.assertTrue(direct.getRemainingFireTicks() > 140, "直撃にも火属性IIの8秒燃焼");
            helper.assertTrue(nearby.getRemainingFireTicks() > 140, "爆風にも火属性IIの8秒燃焼");
            helper.assertTrue(!owner.isOnFire(), "所有者は燃やさない");
            helper.succeed();
        });
    }

    @GameTest(template = "test/empty", timeoutTicks = 40)
    public static void phantasmReachesFurtherWithoutBreakingBlocks(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 2));
        Cow direct = cow(helper, new BlockPos(4, 2, 2));
        Cow distant = cow(helper, new BlockPos(4, 2, 6));
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.TNT);
        RoyalBladeEntity shot = shoot(helper, owner, direct, new ItemStack(Items.DIAMOND_SWORD), true);
        helper.assertTrue(shot.phantasm(), "召喚時のモードを投影に保存");
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(direct.getHealth() < 100F, "幻想の直撃ダメージ");
            helper.assertTrue(distant.getHealth() < 100F, "通常の半径2より遠い相手にも幻想の爆風");
            helper.assertTrue(owner.getHealth() == 100F, "所有者を攻撃しない");
            helper.assertBlockPresent(Blocks.TNT, new BlockPos(4, 1, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "test/empty")
    public static void keyModePreservesUnrelatedComponents(GameTestHelper helper) {
        ItemStack key = KingsTreasure.ITEM.get().getDefaultInstance();
        CompoundTag custom = new CompoundTag();
        custom.putString("test_sentinel", "keep");
        key.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        RoyalBladeEffects.setPhantasm(key, true);
        helper.assertTrue(RoyalBladeEffects.isPhantasm(key), "モードON");
        helper.assertTrue(key.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, 0) == 1, "赤いモデルを同期");
        RoyalBladeEffects.setPhantasm(key, false);
        helper.assertTrue(!RoyalBladeEffects.isPhantasm(key), "モードOFF");
        helper.assertTrue(!key.has(DataComponents.CUSTOM_MODEL_DATA), "通常の金色モデルへ戻す");
        helper.assertTrue("keep".equals(key.get(DataComponents.CUSTOM_DATA).copyTag().getString("test_sentinel")),
                "無関係なCUSTOM_DATAを変更しない");
        helper.succeed();
    }

    private static Cow cow(GameTestHelper helper, BlockPos pos) {
        Cow cow = helper.spawn(EntityType.COW, pos);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        cow.setHealth(100F);
        return cow;
    }

    private static RoyalBladeEntity shoot(GameTestHelper helper, Cow owner, Cow target, ItemStack sword, boolean phantasm) {
        RoyalBladeEntity shot = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        shot.stage(owner, sword, 0, new Vec3(1, 0, 0), 1, phantasm);
        shot.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.getLevel().addFreshEntity(shot);
        shot.launch(target.getBoundingBox().getCenter());
        return shot;
    }
}
