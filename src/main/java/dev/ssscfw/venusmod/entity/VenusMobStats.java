package dev.ssscfw.venusmod.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class VenusMobStats {
    private VenusMobStats() {
    }

    static void apply(LivingEntity mob, double movementMultiplier, double knockbackResistance) {
        multiplyBase(mob, Attributes.MAX_HEALTH, 3.0D);
        multiplyBase(mob, Attributes.ATTACK_DAMAGE, 2.0D);
        multiplyBase(mob, Attributes.MOVEMENT_SPEED, movementMultiplier);

        AttributeInstance knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(knockbackResistance);
        }

        mob.setHealth(mob.getMaxHealth());
    }

    private static void multiplyBase(LivingEntity mob, Holder<Attribute> attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    static void dropGoldNuggets(LivingEntity mob) {
        int count = 1 + mob.getRandom().nextInt(4);
        mob.spawnAtLocation(new ItemStack(Items.GOLD_NUGGET, count));
    }
}
