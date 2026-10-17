package dev.ssscfw.venusmod.mixin;

import dev.ssscfw.venusmod.compat.RapidSlashSpeedRules;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modify SlashBlade's actual grounded rapid_slash movement, on BOTH logical sides.
 * The former ServerPlayer PlayerTickEvent.Post bonus could be overwritten by
 * the client's movement prediction and did not reliably affect the native dash.
 */
@Mixin(Entity.class)
public abstract class RapidSlashMovementMixin {
    private static final TagKey<Item> VENUSMOD$SLASHBLADE_SWORDS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("slashblade", "swords"));

    @Inject(method = "moveRelative(FLnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void venusmod$enhanceRapidSlash(float amount, Vec3 relative, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)
                || !RapidSlashSpeedRules.isRapidSlashImpulse(
                        amount, relative.x, relative.y, relative.z,
                        player.onGround(), player.isInWater())) {
            return;
        }

        ItemStack blade = player.getMainHandItem();
        // The native tag handles vanilla SlashBlade; the class bridge allows addon blades.
        if (!blade.is(VENUSMOD$SLASHBLADE_SWORDS)
                && !SlashBladeEnchantmentCompat.isBlade(blade)) {
            return;
        }

        int level = ModEnchantments.getLevel(
                blade, ModEnchantments.RAPID_SLASH_SPEED, player.registryAccess());
        float bonus = RapidSlashSpeedRules.extraImpulse(amount, level);
        if (bonus > 0.0F) {
            // This passes the same vector through Minecraft's own yaw transform.
            // Extra impulses (max 0.6 / 0.2625) cannot match the native signature,
            // so the nested call never boosts itself recursively.
            ((Entity) (Object) this).moveRelative(bonus, relative);
        }
    }
}
