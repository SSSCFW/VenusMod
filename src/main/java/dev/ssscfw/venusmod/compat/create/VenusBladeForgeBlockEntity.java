package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.registry.VenusPhase2;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeData;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeType;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** GUI内の刀1本を保持し、選択項目を1レベルずつ鍛錬するCreate機械。 */
public final class VenusBladeForgeBlockEntity extends KineticBlockEntity implements MenuProvider, Clearable {
    public static final float REQUIRED_RPM = 64.0F;

    private final ItemStackHandler bladeInv = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && SlashBladeEnchantmentCompat.isBlade(stack);
        }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) sendData();
        }
    };
    private long nextUseGameTime;

    public VenusBladeForgeBlockEntity(BlockPos pos, BlockState state) {
        super(VenusCreateCompat.VENUS_BLADE_FORGE_BE.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    public IItemHandler getBladeHandler() { return bladeInv; }
    public ItemStack getBlade() { return bladeInv.getStackInSlot(0); }

    public BladeUpgradeType getSelectedType() {
        BlockState state = getBlockState();
        return state.hasProperty(VenusBladeForgeBlock.UPGRADE)
                ? state.getValue(VenusBladeForgeBlock.UPGRADE)
                : BladeUpgradeType.BLADE_POWER;
    }

    public void selectNext() { setSelectedType(getSelectedType().next()); }

    public void selectPrevious() {
        BladeUpgradeType[] values = BladeUpgradeType.values();
        int index = (getSelectedType().ordinal() + values.length - 1) % values.length;
        setSelectedType(values[index]);
    }

    private void setSelectedType(BladeUpgradeType type) {
        if (level == null || level.isClientSide || type == null) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(VenusBladeForgeBlock.UPGRADE)
                || state.getValue(VenusBladeForgeBlock.UPGRADE) == type) return;
        level.setBlock(worldPosition, state.setValue(VenusBladeForgeBlock.UPGRADE, type), 3);
        setChanged();
        sendData();
    }

    public boolean tryUpgrade(ServerPlayer player, BladeUpgradeType type) {
        ItemStack blade = bladeInv.getStackInSlot(0);
        if (!SlashBladeEnchantmentCompat.isBlade(blade)) {
            player.displayClientMessage(Component.literal("抜刀剣をセットしてください"), true);
            return true;
        }
        boolean handled = tryUpgrade(player, blade, type);
        if (handled) {
            // Data Componentを書き換えた同一StackもSlot同期で確実にクライアントへ送り直す。
            bladeInv.setStackInSlot(0, blade.copy());
        }
        return handled;
    }

    /** 強化条件・素材消費ロジック。進行キーは所持条件のみで消費しない。 */
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
            player.displayClientMessage(Component.translatable(
                    "message.venusmod.blade_upgrade_rpm", (int) REQUIRED_RPM), true);
            return true;
        }
        if (!contains(player.getInventory(), VenusDimensionContent.VENUS_CORE.get(), 1)) {
            player.displayClientMessage(Component.translatable(
                    "message.venusmod.blade_upgrade_requires_core"), true);
            return true;
        }

        int next = current + 1;
        BladeUpgradeRules.Cost cost = BladeUpgradeRules.cost(type.category(), next);
        if (cost.soulStoneRequired()
                && !contains(player.getInventory(), VenusDimensionContent.VENUS_SOUL_STONE.get(), 1)) {
            player.displayClientMessage(Component.translatable(
                    "message.venusmod.blade_upgrade_requires_soul_stone"), true);
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
            server.playSound(null, worldPosition, SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 1.0F, 1.15F + next * 0.02F);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.1D, worldPosition.getZ() + 0.5D,
                    24, 0.45D, 0.35D, 0.45D, 0.05D);
        }
        player.displayClientMessage(Component.translatable("message.venusmod.blade_upgrade_success",
                Component.translatable("upgrade.venusmod." + type.getSerializedName()),
                next, type.maxLevel()), true);
        setChanged();
        sendData();
        return true;
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.venusmod.venus_blade_forge");
    }

    @Nullable
    @Override public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VenusBladeForgeMenu(containerId, inventory, this);
    }

    @Override public void clearContent() {
        bladeInv.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override public void destroy() {
        super.destroy();
        if (level != null) ItemHelper.dropContents(level, worldPosition, bladeInv);
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("BladeInventory", bladeInv.serializeNBT(registries));
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bladeInv.deserializeNBT(registries, compound.getCompound("BladeInventory"));
        super.read(compound, registries, clientPacket);
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
