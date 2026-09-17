package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import dev.ssscfw.venusmod.treasure.RoyalBladeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実サーバー上の衝突・非破壊性・任意依存を検査する。描画の合格とは区別する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingsTreasureGameTests {
    @GameTest(template = "test/empty")
    public static void recipeAndOptionalDependencies(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "kings_treasure")).isPresent(), "王の財宝レシピが未登録");
        helper.assertTrue(KingsTreasure.ITEM.get().getDefaultInstance().getMaxStackSize() == 1, "キーは単独スタック");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void copyWithoutPersistentItem(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 1));
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("保管刀"));
        original.setDamageValue(7);
        RoyalBladeEntity blade = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        blade.stage(owner, original, 0);
        helper.assertTrue(ItemStack.isSameItemSameComponents(original, blade.blade()), "全コンポーネントを引き継ぐ");
        original.setDamageValue(11);
        helper.assertTrue(blade.blade().getDamageValue() == 7, "原本から独立したコピー");
        helper.assertTrue(!blade.save(new CompoundTag()), "投影をワールド保存しない");
        helper.assertTrue(!RoyalBladeEntity.canDamage(owner, owner), "所有者への攻撃拒否");
        blade.discard();
        helper.assertTrue(original.getCount() == 1 && original.getDamageValue() == 11, "収納時に原本を変更しない");
        helper.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 40)
    public static void impactPreservesTerrainAndDrops(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 2));
        Cow target = cow(helper, new BlockPos(4, 2, 2));
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.TNT);
        Vec3 dropPos = target.position();
        ItemEntity drop = new ItemEntity(helper.getLevel(), dropPos.x, dropPos.y, dropPos.z, new ItemStack(Items.DIAMOND));
        drop.setNoGravity(true);
        helper.getLevel().addFreshEntity(drop);
        RoyalBladeEntity blade = shoot(helper, owner, target.getBoundingBox().getCenter());
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(blade.isRemoved(), "命中後に投影を除去");
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "対象へダメージ");
            helper.assertTrue(owner.getHealth() == owner.getMaxHealth(), "所有者を傷つけない");
            helper.assertBlockPresent(Blocks.TNT, new BlockPos(4, 1, 2));
            helper.assertTrue(drop.isAlive() && drop.getItem().is(Items.DIAMOND), "ドロップを破壊しない");
            helper.succeed();
        });
    }

    @GameTest(template = "test/empty", timeoutTicks = 40)
    public static void wallStopsFlightAndBlast(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 2));
        Cow target = cow(helper, new BlockPos(4, 2, 2));
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        RoyalBladeEntity blade = shoot(helper, owner, target.getBoundingBox().getCenter());
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(blade.isRemoved(), "壁で射出体を除去");
            helper.assertTrue(target.getHealth() == target.getMaxHealth(), "壁の向こうへ爆風を通さない");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "test/empty")
    public static void ownedPetIsProtected(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 1));
        Wolf pet = helper.spawn(EntityType.WOLF, new BlockPos(3, 2, 1));
        pet.setOwnerUUID(owner.getUUID());
        helper.assertTrue(!RoyalBladeEntity.canDamage(owner, pet), "所有者UUIDが同じペットは保護");
        helper.succeed();
    }

    private static Cow cow(GameTestHelper helper, BlockPos position) {
        Cow cow = helper.spawn(EntityType.COW, position);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }
    private static RoyalBladeEntity shoot(GameTestHelper helper, Cow owner, Vec3 target) {
        RoyalBladeEntity blade = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        blade.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 0);
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        blade.setPos(start);
        helper.getLevel().addFreshEntity(blade);
        blade.launch(target);
        return blade;
    }
}
