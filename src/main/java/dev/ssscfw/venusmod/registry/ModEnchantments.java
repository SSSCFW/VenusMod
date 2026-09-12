package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Data-driven enchantments supplied from data/venusmod/enchantment/*.json.
 */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> BLADE_RETENTION = key("blade_retention");
    public static final ResourceKey<Enchantment> STYLISH = key("stylish");
    public static final ResourceKey<Enchantment> SUMMONED_SWORD_DAMAGE = key("summoned_sword_damage");

    private static final ResourceKey<Enchantment>[] BLADE_SPECIAL_ENCHANTMENTS = new ResourceKey[] {
            BLADE_RETENTION,
            STYLISH,
            SUMMONED_SWORD_DAMAGE
    };

    private ModEnchantments() {
    }

    public static int getLevel(ItemStack stack, ResourceKey<Enchantment> enchantment,
                               HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        return EnchantmentHelper.getTagEnchantmentLevel(holder, stack);
    }

    public static boolean hasAnyBladeSpecialEnchantment(ItemStack stack, HolderLookup.Provider registries) {
        for (ResourceKey<Enchantment> enchantment : BLADE_SPECIAL_ENCHANTMENTS) {
            if (getLevel(stack, enchantment, registries) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies the three Venus SlashBlade enchantments from a Soul Fragment carrier onto
     * the refined blade. Matching equal levels combine like a vanilla enchanted book:
     * they gain one level, capped at that enchantment's max level.
     */
    public static void mergeBladeSpecialEnchantments(ItemStack carrier, ItemStack blade,
                                                      HolderLookup.Provider registries) {
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> enchantmentKey : BLADE_SPECIAL_ENCHANTMENTS) {
            Holder<Enchantment> enchantment = enchantments.getOrThrow(enchantmentKey);
            int incomingLevel = EnchantmentHelper.getTagEnchantmentLevel(enchantment, carrier);
            if (incomingLevel <= 0) {
                continue;
            }

            int currentLevel = EnchantmentHelper.getTagEnchantmentLevel(enchantment, blade);
            int mergedLevel = incomingLevel == currentLevel
                    ? Math.min(incomingLevel + 1, enchantment.value().getMaxLevel())
                    : Math.max(incomingLevel, currentLevel);

            EnchantmentHelper.updateEnchantments(blade, mutable -> mutable.set(enchantment, mergedLevel));
        }
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, path));
    }
}
