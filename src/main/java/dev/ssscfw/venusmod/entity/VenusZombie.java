package dev.ssscfw.venusmod.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Method;

public class VenusZombie extends Zombie {
    private static final int DASH_DAMAGE_BOOST_TICKS = 30;
    private static final String SLASHBLADE_MOD_ID = "slashblade";
    private static final String SLASHBLADE_PACKAGE_PREFIX = "mods.flammpfeil.slashblade.";
    private static final String SLASHBLADE_KNOCKBACK_FACTOR_KEY = "knockback_factor";
    private static final ResourceLocation SLASHBLADE_MOB_EFFECT_ATTACHMENT_ID =
            ResourceLocation.fromNamespaceAndPath(SLASHBLADE_MOD_ID, "mob_effect");

    private static AttachmentType<?> slashBladeMobEffectAttachment;
    private static Method slashBladeSetStunLimitMethod;
    private static Method slashBladeSetStunTimeoutMethod;

    private int dashDamageBoostTicks;

    public VenusZombie(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        VenusMobStats.apply(this, 1.2D, 1.0D);
    }

    @Override
    public void tick() {
        // SlashBlade's normal combo attacks apply a managed stun to PathfinderMob
        // targets. Its StunGoal owns MOVE/JUMP/LOOK/TARGET while that stun is active,
        // which makes the zombie appear to flinch/freeze even though knockback and
        // hurt animation have already been suppressed. Set this entity's SlashBlade
        // stun limit to zero before AI ticks so newly queued combo stuns are invalid.
        disableSlashBladeStun();

        super.tick();

        // Clear again after the entity tick in case another SlashBlade callback queued
        // stun state during this tick. The zero limit also makes subsequent setStun
        // calls resolve to an immediately expired timeout.
        disableSlashBladeStun();

        if (dashDamageBoostTicks > 0) {
            dashDamageBoostTicks--;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean slashBladeHit = isSlashBladeDamage(source);
        Vec3 movementBeforeHit = slashBladeHit ? getDeltaMovement() : Vec3.ZERO;
        boolean wasInsideInvulnerabilityFrames = this.invulnerableTime > 0;

        if (slashBladeHit) {
            // SlashBlade stores a one-shot knockback mode in persistent data. Venus
            // zombies are completely knockback immune, so never leave that value
            // queued for a later unrelated knockback event.
            getPersistentData().remove(SLASHBLADE_KNOCKBACK_FACTOR_KEY);
            disableSlashBladeStun();
        }

        boolean hurt = super.hurt(source, amount);

        if (slashBladeHit) {
            getPersistentData().remove(SLASHBLADE_KNOCKBACK_FACTOR_KEY);
            disableSlashBladeStun();
            setDeltaMovement(movementBeforeHit);
            hurtTime = 0;
            hurtDuration = 0;
        }

        if (hurt && wasInsideInvulnerabilityFrames && !level().isClientSide) {
            Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            reactToInvulnerabilityPiercingHit(attacker);
        }

        return hurt;
    }

    @Override
    public void handleDamageEvent(DamageSource source) {
        if (isSlashBladeDamage(source)) {
            // The damage packet still updates health normally; skipping vanilla's
            // damage-event animation prevents the visible hurt/flinch reaction.
            hurtTime = 0;
            hurtDuration = 0;
            return;
        }
        super.handleDamageEvent(source);
    }

    private static boolean isSlashBladeDamage(DamageSource source) {
        Entity directEntity = source.getDirectEntity();
        Entity attacker = source.getEntity();

        return isSlashBladeEntity(directEntity)
                || isSlashBladeEntity(attacker)
                || isHoldingSlashBlade(attacker);
    }

    private static boolean isSlashBladeEntity(Entity entity) {
        return entity != null && entity.getClass().getName().startsWith(SLASHBLADE_PACKAGE_PREFIX);
    }

    private static boolean isHoldingSlashBlade(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return isSlashBladeItem(living.getMainHandItem()) || isSlashBladeItem(living.getOffhandItem());
    }

    private static boolean isSlashBladeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && SLASHBLADE_MOD_ID.equals(id.getNamespace());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void disableSlashBladeStun() {
        AttachmentType<?> attachment = slashBladeMobEffectAttachment;
        if (attachment == null) {
            attachment = NeoForgeRegistries.ATTACHMENT_TYPES.get(SLASHBLADE_MOB_EFFECT_ATTACHMENT_ID);
            if (attachment == null) {
                return;
            }
            slashBladeMobEffectAttachment = attachment;
        }

        Object effectState = getData((AttachmentType) attachment);
        if (effectState == null) {
            return;
        }

        try {
            Method setStunLimit = slashBladeSetStunLimitMethod;
            if (setStunLimit == null) {
                setStunLimit = effectState.getClass().getMethod("setStunLimit", int.class);
                slashBladeSetStunLimitMethod = setStunLimit;
            }

            Method setStunTimeOut = slashBladeSetStunTimeoutMethod;
            if (setStunTimeOut == null) {
                setStunTimeOut = effectState.getClass().getMethod("setStunTimeOut", long.class);
                slashBladeSetStunTimeoutMethod = setStunTimeOut;
            }

            setStunLimit.invoke(effectState, 0);
            setStunTimeOut.invoke(effectState, -1L);
        } catch (ReflectiveOperationException ignored) {
            // Optional compatibility: VenusMod must still run when SlashBlade is absent
            // or when a different SlashBlade version exposes a different attachment.
        }
    }

    public void reactToInvulnerabilityPiercingHit(Entity attacker) {
        if (!(attacker instanceof LivingEntity livingAttacker) || !livingAttacker.isAlive()) {
            return;
        }

        setTarget(livingAttacker);
        dashDamageBoostTicks = DASH_DAMAGE_BOOST_TICKS;

        Vec3 direction = livingAttacker.position().subtract(position());
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            Vec3 dash = horizontal.normalize().scale(1.35D);
            setDeltaMovement(dash.x, Math.max(getDeltaMovement().y, 0.2D), dash.z);
            hasImpulse = true;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (dashDamageBoostTicks <= 0) {
            return super.doHurtTarget(target);
        }

        AttributeInstance attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return super.doHurtTarget(target);
        }

        double original = attackDamage.getBaseValue();
        attackDamage.setBaseValue(original * 1.5D);
        try {
            return super.doHurtTarget(target);
        } finally {
            attackDamage.setBaseValue(original);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Venus zombies are completely immune to knockback.
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return EntityType.ZOMBIE.getDefaultLootTable();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(level, source, causedByPlayer);
        VenusMobStats.dropGoldNuggets(this);
    }
}
