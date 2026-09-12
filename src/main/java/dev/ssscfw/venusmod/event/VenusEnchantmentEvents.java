package dev.ssscfw.venusmod.event;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Runtime effects for VenusMod's data-driven SlashBlade enchantments. */
public final class VenusEnchantmentEvents {
    private static final String RETAINED_BLADES_KEY = "VenusRetainedBlades";
    private static final String RETAINED_SLOT_KEY = "Slot";
    private static final String RETAINED_STACK_KEY = "Stack";

    private VenusEnchantmentEvents() {
    }

    /** Adds +1 raw summoned-sword damage per enchantment level before armor reductions. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!SlashBladeEnchantmentCompat.isSummonedSword(direct)) {
            return;
        }

        Entity owner = event.getSource().getEntity();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return;
        }

        ItemStack blade = livingOwner.getMainHandItem();
        if (!SlashBladeEnchantmentCompat.isBlade(blade)) {
            return;
        }

        int level = ModEnchantments.getLevel(
                blade,
                ModEnchantments.SUMMONED_SWORD_DAMAGE,
                livingOwner.registryAccess());
        if (level > 0) {
            event.setAmount(event.getAmount() + level);
        }
    }

    /**
     * SlashBlade awards its normal concentration-rank points in LivingDamageEvent.Pre.
     * Post runs after that award and adds +25% of the same native gain per Stylish level.
     */
    public static void onDamagePost(LivingDamageEvent.Post event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) {
            return;
        }

        ItemStack blade = livingAttacker.getMainHandItem();
        if (!SlashBladeEnchantmentCompat.isBlade(blade)) {
            return;
        }

        int level = ModEnchantments.getLevel(
                blade,
                ModEnchantments.STYLISH,
                livingAttacker.registryAccess());
        if (level > 0) {
            SlashBladeEnchantmentCompat.addStylishRankBonus(livingAttacker, event.getSource(), level);
        }
    }

    /**
     * Removes retained blades from the inventory immediately before vanilla death drops
     * are produced. They are serialized on the old player and restored by Clone.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        persistent.remove(RETAINED_BLADES_KEY);

        Inventory inventory = player.getInventory();
        ListTag retained = new ListTag();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!SlashBladeEnchantmentCompat.isBlade(stack)) {
                continue;
            }

            int level = ModEnchantments.getLevel(
                    stack,
                    ModEnchantments.BLADE_RETENTION,
                    player.registryAccess());
            if (level <= 0) {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            entry.putInt(RETAINED_SLOT_KEY, slot);
            entry.put(RETAINED_STACK_KEY, stack.saveOptional(player.registryAccess()));
            retained.add(entry);
            inventory.setItem(slot, ItemStack.EMPTY);
        }

        if (!retained.isEmpty()) {
            persistent.put(RETAINED_BLADES_KEY, retained);
        }
    }

    /** Restores retained blades into their original slots after a death respawn. */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        CompoundTag originalData = event.getOriginal().getPersistentData();
        if (!originalData.contains(RETAINED_BLADES_KEY, Tag.TAG_LIST)) {
            return;
        }

        ListTag retained = originalData.getList(RETAINED_BLADES_KEY, Tag.TAG_COMPOUND);
        Inventory inventory = newPlayer.getInventory();
        for (int i = 0; i < retained.size(); i++) {
            CompoundTag entry = retained.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(
                    newPlayer.registryAccess(),
                    entry.getCompound(RETAINED_STACK_KEY));
            if (stack.isEmpty()) {
                continue;
            }

            int slot = entry.getInt(RETAINED_SLOT_KEY);
            if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack);
            } else if (!inventory.add(stack)) {
                newPlayer.drop(stack, false);
            }
        }

        originalData.remove(RETAINED_BLADES_KEY);
        newPlayer.getPersistentData().remove(RETAINED_BLADES_KEY);
    }
}
