package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.registry.VenusPhase2;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeData;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeType;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** プレイヤーが手に持った刀を選択項目ごとに1レベルずつ鍛錬するCreate機械。 */
public final class VenusBladeForgeBlockEntity extends KineticBlockEntity {
    public static final float REQUIRED_RPM = 64.0F;
    private long nextUseGameTime;

    public VenusBladeForgeBlockEntity(BlockPos pos, BlockState state) {
        super(VenusCreateCompat.VENUS_BLADE_FORGE_BE.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    public boolean tryUpgrade(ServerPlayer player, ItemStack blade, BladeUpgradeType type) {
        if (level == null || level.isClientSide || !SlashBladeEnchantmentCompat.isBlade(blade)) return false;
        if (level.getGameTime() < nextUseGameTime) return true;
        nextUseGameTime = level.getGameTime() + 6;

        int current = BladeUpgradeData.getLevel(blade, type);
        if (current >= type.maxLevel()) {
            player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_max",
                    Component.translatable("upgrade.venusmod." + type.getSerializedName()), type.maxLevel()), true);
            return true;
        }
        if (!Float.isFinite(getSpeed()) || Math.abs(getSpeed()) < REQUIRED_RPM) {
            player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_rpm", (int) REQUIRED_RPM), true);
            return true;
        }
        if (!contains(player.getInventory(), VenusDimensionContent.VENUS_CORE.get(), 1)) {
            player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_requires_core"), true);
            return true;
        }

        int next = current + 1;
        BladeUpgradeRules.Cost cost = BladeUpgradeRules.cost(type.category(), next);
        if (cost.soulStoneRequired() && !contains(player.getInventory(), VenusDimensionContent.VENUS_SOUL_STONE.get(), 1)) {
            player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_requires_soul_stone"), true);
            return true;
        }

        Item pressureAlloy = VenusPhase2.PRESSURE_ALLOY.get();
        Item nickel = ModItems.NICKEL_INGOT.get();
        Item venesite = VenusPhase2.VENESITE.get();
        Item sulfur = VenusPhase2.SULFUR.get();
        Item missing = firstMissing(player.getInventory(), cost, pressureAlloy, nickel, venesite, sulfur);
        if (missing != null) {
            int need = requiredCount(cost, missing, pressureAlloy, nickel, venesite, sulfur);
            int have = count(player.getInventory(), missing);
            player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_missing",
                    new ItemStack(missing).getHoverName(), Math.max(0, need - have)), true);
            showStatus(player, blade, type);
            return true;
        }

        if (!player.getAbilities().instabuild) {
            consume(player.getInventory(), pressureAlloy, cost.pressureAlloy());
            consume(player.getInventory(), nickel, cost.nickel());
            consume(player.getInventory(), venesite, cost.venesite());
            consume(player.getInventory(), sulfur, cost.sulfur());
            player.getInventory().setChanged();
        }

        BladeUpgradeData.setLevel(blade, type, next);
        if (level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.15F + next * 0.02F);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.1D, worldPosition.getZ() + 0.5D,
                    24, 0.45D, 0.35D, 0.45D, 0.05D);
        }
        player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_success",
                Component.translatable("upgrade.venusmod." + type.getSerializedName()), next, type.maxLevel()), true);
        setChanged();
        return true;
    }

    public void showStatus(ServerPlayer player, ItemStack blade, BladeUpgradeType type) {
        int current = SlashBladeEnchantmentCompat.isBlade(blade) ? BladeUpgradeData.getLevel(blade, type) : 0;
        if (current >= type.maxLevel()) {
            player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_status_max",
                    Component.translatable("upgrade.venusmod." + type.getSerializedName()), current, type.maxLevel()), true);
            return;
        }
        BladeUpgradeRules.Cost cost = BladeUpgradeRules.cost(type.category(), current + 1);
        player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_status",
                Component.translatable("upgrade.venusmod." + type.getSerializedName()),
                current, type.maxLevel(), current + 1,
                cost.pressureAlloy(), cost.nickel(), cost.venesite(), cost.sulfur(),
                Component.translatable(cost.soulStoneRequired()
                        ? "message.venusmod.blade_upgrade_soul_required"
                        : "message.venusmod.blade_upgrade_soul_not_required")), true);
    }

    private static Item firstMissing(Inventory inv, BladeUpgradeRules.Cost cost,
                                     Item pressureAlloy, Item nickel, Item venesite, Item sulfur) {
        if (!contains(inv, pressureAlloy, cost.pressureAlloy())) return pressureAlloy;
        if (!contains(inv, nickel, cost.nickel())) return nickel;
        if (!contains(inv, venesite, cost.venesite())) return venesite;
        if (!contains(inv, sulfur, cost.sulfur())) return sulfur;
        return null;
    }

    private static int requiredCount(BladeUpgradeRules.Cost cost, Item item,
                                     Item pressureAlloy, Item nickel, Item venesite, Item sulfur) {
        if (item == pressureAlloy) return cost.pressureAlloy();
        if (item == nickel) return cost.nickel();
        if (item == venesite) return cost.venesite();
        if (item == sulfur) return cost.sulfur();
        return 0;
    }

    private static boolean contains(Inventory inv, Item item, int required) {
        return required <= 0 || count(inv, item) >= required;
    }

    private static int count(Inventory inv, Item item) {
        int total = 0;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void consume(Inventory inv, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inv.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }
}
