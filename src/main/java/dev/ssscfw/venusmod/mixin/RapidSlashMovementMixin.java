package dev.ssscfw.venusmod.mixin;

import dev.ssscfw.venusmod.compat.RapidSlashSpeedRules;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private boolean venusmod$wasGroundedBeforeMove;
    @Unique
    private boolean venusmod$snappingDown;

    /**
     * Keep the real collision-derived ground flag across short downward ledges.
     * Mark the state BEFORE the horizontal movement, as vanilla clears onGround
     * after the player leaves the previous block.
     */
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"))
    private void venusmod$captureGroundBeforeRapidSlashMove(MoverType mover, Vec3 movement, CallbackInfo ci) {
        if (venusmod$snappingDown) {
            return;
        }
        venusmod$wasGroundedBeforeMove =
                (mover == MoverType.SELF || mover == MoverType.PLAYER)
                && (Object) this instanceof Player player
                && player.onGround();
    }

    /**
     * A normal airborne transition disables rapid_slash's own onGround-guarded
     * acceleration, making chained dashes snag on one-block down-steps.
     * Sweep down ONLY if there is a collision surface within one step.
     * The nested vanilla move resolves collision and sets onGround; we never
     * fake onGround over empty air, ignore large drops and avoid tunnelling.
     */
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("TAIL"))
    private void venusmod$followRapidSlashDownStep(MoverType mover, Vec3 movement, CallbackInfo ci) {
        if (venusmod$snappingDown) {
            return;
        }
        boolean wasGrounded = venusmod$wasGroundedBeforeMove;
        venusmod$wasGroundedBeforeMove = false;

        if (!((Object) this instanceof Player player)
                || player.isPassenger()
                || player.getAbilities().flying
                || player.isFallFlying()
                || player.isInWater()
                || player.isInLava()
                || !RapidSlashSpeedRules.shouldFollowDownStep(
                        wasGrounded, player.onGround(),
                        movement.x, movement.y, movement.z,
                        player.getDeltaMovement().y)) {
            return;
        }
        if (!SlashBladeEnchantmentCompat.isRapidSlashGroundFollowCombo(player)) {
            return;
        }

        // The player's bounding box is tested at the *new horizontal position*.
        // NoCollision=true means this is a cliff/gap rather than a reachable step.
        if (player.level().noCollision(player,
                player.getBoundingBox().move(0.0D, -RapidSlashSpeedRules.MAX_STEP_DOWN, 0.0D))) {
            return;
        }

        venusmod$snappingDown = true;
        try {
            // Collision-limited vertical movement (including slabs and stairs),
            // not teleportation and not setOnGround(true).
            ((Entity) (Object) this).move(MoverType.SELF, new Vec3(
                    0.0D, -RapidSlashSpeedRules.MAX_STEP_DOWN, 0.0D));
        } finally {
            venusmod$snappingDown = false;
        }
    }

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
