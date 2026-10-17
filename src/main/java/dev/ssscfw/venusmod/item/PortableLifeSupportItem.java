package dev.ssscfw.venusmod.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 金星環境用の携帯生命維持装置。 */
public final class PortableLifeSupportItem extends Item {
    private static final int ENCHANTMENT_VALUE = 15;

    public PortableLifeSupportItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }
}
