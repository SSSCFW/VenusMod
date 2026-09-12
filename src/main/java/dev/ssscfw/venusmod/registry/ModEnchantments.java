package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
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

    private ModEnchantments() {
    }

    public static int getLevel(ItemStack stack, ResourceKey<Enchantment> enchantment,
                               HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return 0;
        }
        var holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        return EnchantmentHelper.getTagEnchantmentLevel(holder, stack);
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, path));
    }
}
