package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import dev.ssscfw.venusmod.treasure.RoyalBladeEntity;
import dev.ssscfw.venusmod.treasure.TreasureRules;
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

/** 実サーバー上の衝突・非破壊性・多段命中・射出方向を検査する。描画の合格とは区別する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingsTreasureGameTests {
    @GameTest(template = "test/empty")
    public static void recipeAndOptionalDependencies(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "kings_treasure")).isPresent(),
                "王の財宝レシピが未登録");
        helper.assertTrue(KingsTreasure.ITEM.get().getDefaultInstance().getMaxStackSize() == 1,
                "キーは単独スタック");
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
        helper.assertTrue(ItemStack.isSameItemSameComponents(original, blade.blade()),
                "全コンポーネントを引き継ぐ");
        original.setDamageValue(11);
        helper.assertTrue(blade.blade().getDamageValue() == 7, "原本から独立したコピー");
        helper.assertTrue(!blade.save(new CompoundTag()), "投影をワールド保存しない");
        helper.assertTrue(!RoyalBladeEntity.canDamage(owner, owner), "所有者への攻撃拒否");
        blade.discard();
        helper.assertTrue(original.getCount() == 1 && original.getDamageValue() == 11,
                "収納時に原本を変更しない");
        helper.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 20)
    public static void stagedBladeStaysAtSummonPosition(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(2, 2, 2));
        RoyalBladeEntity blade = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        Vec3 summonDirection = new Vec3(0.35D, -0.15D, 1.0D).normalize();
        blade.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 0, summonDirection);
        Vec3 staged = blade.position();
        float stagedYaw = blade.getYRot();
        float stagedPitch = blade.getXRot();
        helper.getLevel().addFreshEntity(blade);
        owner.setPos(owner.getX() + 5.0D, owner.getY(), owner.getZ() + 2.0D);
        owner.setYRot(owner.getYRot() + 90.0F);
        owner.setXRot(owner.getXRot() + 30.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(blade.position().distanceToSqr(staged) < 1.0E-6D,
                    "展開した刀は召喚座標からプレイヤーを追従しない");
            helper.assertTrue(Math.abs(blade.getYRot() - stagedYaw) < 1.0E-4F
                            && Math.abs(blade.getXRot() - stagedPitch) < 1.0E-4F,
                    "刀の向きは召喚時の方向から変化しない");
            helper.succeed();
        });
    }

    @GameTest(template = "test/empty")
    public static void stagedBladeSpacingIsExpanded(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(2, 2, 2));
        RoyalBladeEntity first = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        RoyalBladeEntity second = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        first.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 0);
        second.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 1);
        helper.assertTrue(first.position().distanceTo(second.position()) > 0.80D,
                "隣接する刀の間隔が狭すぎる");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void parallelVolleyKeepsSpread(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(3, 2, 3));
        Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 focus = owner.getEyePosition().add(forward.scale(TreasureRules.CONVERGENCE_DISTANCE));
        RoyalBladeEntity left = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        RoyalBladeEntity right = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        left.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 0, forward);
        right.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 7, forward);
        double startingSpread = left.position().distanceTo(right.position());
        left.launch(forward, focus, TreasureRules.VolleyMode.PARALLEL.convergence());
        right.launch(forward, focus, TreasureRules.VolleyMode.PARALLEL.convergence());
        Vec3 leftDirection = left.getDeltaMovement().normalize();
        Vec3 rightDirection = right.getDeltaMovement().normalize();
        helper.assertTrue(leftDirection.distanceToSqr(rightDirection) < 1.0E-8D,
                "通常召喚の刀が平行射出になっていない");
        double nextSpread = left.position().add(leftDirection).distanceTo(right.position().add(rightDirection));
        helper.assertTrue(Math.abs(nextSpread - startingSpread) < 1.0E-6D,
                "平行射出で刀同士の間隔が収束している");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void shiftVolleyConvergesModerately(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(3, 2, 3));
        Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 focus = owner.getEyePosition().add(forward.scale(TreasureRules.CONVERGENCE_DISTANCE));
        RoyalBladeEntity left = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        RoyalBladeEntity right = new RoyalBladeEntity(KingsTreasure.BLADE.get(), helper.getLevel());
        left.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 0, forward);
        right.stage(owner, new ItemStack(Items.DIAMOND_SWORD), 7, forward);
        double startingSpread = left.position().distanceTo(right.position());
        double convergence = TreasureRules.VolleyMode.MEDIUM_CONVERGENCE.convergence();
        left.launch(forward, focus, convergence);
        right.launch(forward, focus, convergence);
        Vec3 leftDirection = left.getDeltaMovement().normalize();
        Vec3 rightDirection = right.getDeltaMovement().normalize();
        double nextSpread = left.position().add(leftDirection).distanceTo(right.position().add(rightDirection));
        helper.assertTrue(nextSpread < startingSpread,
                "Shift召喚の射出が中央へ収束していない");
        helper.assertTrue(nextSpread > startingSpread * 0.75D,
                "Shift召喚の収束率が高すぎて一箇所へ集まりすぎる");
        helper.assertTrue(leftDirection.distanceToSqr(rightDirection) > 1.0E-6D,
                "中程度収束が完全平行になっている");
        helper.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 40)
    public static void impactPreservesTerrainAndDrops(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 2));
        Cow target = cow(helper, new BlockPos(4, 2, 2));
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.TNT);
        Vec3 dropPos = target.position();
        ItemEntity drop = new ItemEntity(helper.getLevel(), dropPos.x, dropPos.y, dropPos.z,
                new ItemStack(Items.DIAMOND));
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
    public static void simultaneousBladesIgnoreInvulnerabilityFrames(GameTestHelper helper) {
        Cow owner = cow(helper, new BlockPos(1, 2, 2));
        Cow target = cow(helper, new BlockPos(4, 2, 2));
        shoot(helper, owner, target.getBoundingBox().getCenter());
        shoot(helper, owner, target.getBoundingBox().getCenter());
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!target.isAlive() || target.getHealth() <= 0.0F,
                    "2本同時命中が無敵時間で1ヒットに潰れている");
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
