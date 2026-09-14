package dev.ssscfw.venusmod.entity;

import dev.ssscfw.venusmod.compat.SlashBladeCompat;
import dev.ssscfw.venusmod.entity.ai.VenusSlashBladeAttackGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
    public static final String NBT_SLASHBLADE_WIELDER = "VenusSlashBladeWielder";
    public static final int BLADE_TECHNIQUE_NONE = 0;
    public static final int BLADE_TECHNIQUE_COMBO = 1;
    public static final int BLADE_TECHNIQUE_RAPID_SLASH = 2;

    private static final EntityDataAccessor<Boolean> DATA_SLASHBLADE_WIELDER =
            SynchedEntityData.defineId(VenusZombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BLADE_TECHNIQUE =
            SynchedEntityData.defineId(VenusZombie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BLADE_TECHNIQUE_TICK =
            SynchedEntityData.defineId(VenusZombie.class, EntityDataSerializers.INT);

    private static final int DASH_DAMAGE_BOOST_TICKS = 30;
    private static final int BLADE_COMBO_COOLDOWN_MIN_TICKS = 28;
    private static final int BLADE_COMBO_COOLDOWN_RANDOM_TICKS = 13;
    private static final int RAPID_SLASH_COOLDOWN_MIN_TICKS = 40;
    private static final int RAPID_SLASH_COOLDOWN_RANDOM_TICKS = 21;
    private static final double BLADE_INTERACTION_RANGE = 7.0D;
    private static final String SLASHBLADE_PACKAGE_PREFIX = "mods.flammpfeil.slashblade.";
    private static final String SLASHBLADE_KNOCKBACK_FACTOR_KEY = "knockback_factor";
    private static final ResourceLocation SLASHBLADE_MOB_EFFECT_ATTACHMENT_ID =
            ResourceLocation.fromNamespaceAndPath(SlashBladeCompat.MOD_ID, "mob_effect");

    private static AttachmentType<?> slashBladeMobEffectAttachment;
    private static Method slashBladeSetStunLimitMethod;
    private static Method slashBladeSetStunTimeoutMethod;

    private int dashDamageBoostTicks;
    private int bladeTechniqueCooldown;
    private EntityType<?> bladeDamageTargetType;
    private int bladeDamageTargetTicks;
    private boolean applyingBladeDamage;

    public VenusZombie(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        VenusMobStats.apply(this, 1.2D, 1.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new VenusSlashBladeAttackGoal(this, 1.2D));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SLASHBLADE_WIELDER, false);
        builder.define(DATA_BLADE_TECHNIQUE, BLADE_TECHNIQUE_NONE);
        builder.define(DATA_BLADE_TECHNIQUE_TICK, 0);
    }

    @Override
    public void tick() {
        disableSlashBladeStun();
        super.tick();
        disableSlashBladeStun();

        if (!level().isClientSide) {
            if (isSlashBladeWielder()) {
                ensureSlashBladeEquipped();
                applyBladeReach();
                tickBladeTechnique();
            }

            if (bladeTechniqueCooldown > 0) {
                bladeTechniqueCooldown--;
            }
            if (bladeDamageTargetTicks > 0) {
                bladeDamageTargetTicks--;
            } else {
                bladeDamageTargetType = null;
            }
        }

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
            hurtTime = 0;
            hurtDuration = 0;
            return;
        }
        super.handleDamageEvent(source);
    }

    public boolean isSlashBladeWielder() {
        return entityData.get(DATA_SLASHBLADE_WIELDER);
    }

    public void setSlashBladeWielder(boolean value) {
        entityData.set(DATA_SLASHBLADE_WIELDER, value);
        if (value && !level().isClientSide) {
            ensureSlashBladeEquipped();
            applyBladeReach();
        }
    }

    public boolean hasSlashBladeEquipped() {
        return SlashBladeCompat.isSlashBladeItem(getMainHandItem());
    }

    public boolean isBladeTechniqueActive() {
        return entityData.get(DATA_BLADE_TECHNIQUE) != BLADE_TECHNIQUE_NONE;
    }

    public int getBladeTechnique() {
        return entityData.get(DATA_BLADE_TECHNIQUE);
    }

    public int getBladeTechniqueTick() {
        return entityData.get(DATA_BLADE_TECHNIQUE_TICK);
    }

    public int getBladeTechniqueCooldown() {
        return bladeTechniqueCooldown;
    }

    public boolean isApplyingBladeDamage() {
        return applyingBladeDamage;
    }

    public void startBladeCombo(LivingEntity target) {
        if (level().isClientSide || isBladeTechniqueActive() || bladeTechniqueCooldown > 0 || target == null) {
            return;
        }
        bindBladeDamageTarget(target);
        entityData.set(DATA_BLADE_TECHNIQUE, BLADE_TECHNIQUE_COMBO);
        entityData.set(DATA_BLADE_TECHNIQUE_TICK, 0);
    }

    public void startRapidSlash(LivingEntity target) {
        if (level().isClientSide || isBladeTechniqueActive() || bladeTechniqueCooldown > 0 || target == null) {
            return;
        }
        bindBladeDamageTarget(target);
        entityData.set(DATA_BLADE_TECHNIQUE, BLADE_TECHNIQUE_RAPID_SLASH);
        entityData.set(DATA_BLADE_TECHNIQUE_TICK, 0);
    }

    public boolean canBladeDamage(LivingEntity victim) {
        EntityType<?> allowedType = bladeDamageTargetType;
        if (allowedType == null) {
            LivingEntity currentTarget = getTarget();
            allowedType = currentTarget != null ? currentTarget.getType() : null;
        }
        return allowedType == null || victim.getType() == allowedType;
    }

    private void bindBladeDamageTarget(LivingEntity target) {
        bladeDamageTargetType = target.getType();
        bladeDamageTargetTicks = 40;
    }

    private void tickBladeTechnique() {
        int technique = getBladeTechnique();
        if (technique == BLADE_TECHNIQUE_NONE) {
            return;
        }

        int tick = getBladeTechniqueTick();
        if (technique == BLADE_TECHNIQUE_COMBO) {
            tickBladeCombo(tick);
            if (tick >= 21) {
                finishBladeTechnique(BLADE_COMBO_COOLDOWN_MIN_TICKS
                        + getRandom().nextInt(BLADE_COMBO_COOLDOWN_RANDOM_TICKS));
                return;
            }
        } else if (technique == BLADE_TECHNIQUE_RAPID_SLASH) {
            tickRapidSlash(tick);
            if (tick >= 8) {
                finishBladeTechnique(RAPID_SLASH_COOLDOWN_MIN_TICKS
                        + getRandom().nextInt(RAPID_SLASH_COOLDOWN_RANDOM_TICKS));
                return;
            }
        }

        entityData.set(DATA_BLADE_TECHNIQUE_TICK, tick + 1);
    }

    private void tickBladeCombo(int tick) {
        switch (tick) {
            case 0 -> performBladeSlash(-10.0F, true, 0.44D, 6.0D);
            case 4 -> performBladeSlash(170.0F, true, 0.44D, 6.0D);
            case 8 -> performBladeSlash(-61.0F, false, 0.44D, 6.0D);
            case 11 -> performBladeSlash(138.0F, false, 0.44D, 6.0D);
            case 15 -> performBladeSlash(45.0F, false, 0.44D, 6.0D);
            case 17 -> performBladeSlash(50.0F, true, 0.44D, 6.0D);
            default -> {
            }
        }
    }

    private void tickRapidSlash(int tick) {
        if (tick == 0) {
            dashTowardBladeTarget();
        } else if (tick == 2) {
            performBladeSlash(30.0F, false, 1.0D, 7.0D);
        } else if (tick == 3) {
            performBladeSlash(210.0F, false, 1.0D, 7.0D);
        }
    }

    private void performBladeSlash(float roll, boolean mute, double comboRatio, double reach) {
        if (!hasSlashBladeEquipped()) {
            finishBladeTechnique(10);
            return;
        }

        SlashBladeCompat.doSlash(this, roll, mute, false, comboRatio);
        applyBladeDamageToTargetSpecies(comboRatio, reach);
    }

    private void applyBladeDamageToTargetSpecies(double comboRatio, double reach) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        bindBladeDamageTarget(target);

        double baseDamage = getAttributeValue(Attributes.ATTACK_DAMAGE);
        float amount = (float) Math.max(1.0D, baseDamage * comboRatio);
        Vec3 look = getLookAngle();

        for (LivingEntity victim : level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(reach),
                entity -> entity != this && entity.isAlive() && canBladeDamage(entity))) {
            Vec3 toVictim = victim.position().subtract(position());
            double distanceSqr = toVictim.lengthSqr();
            if (distanceSqr > reach * reach || distanceSqr < 1.0E-4D) {
                continue;
            }

            Vec3 horizontal = new Vec3(toVictim.x, 0.0D, toVictim.z);
            Vec3 lookHorizontal = new Vec3(look.x, 0.0D, look.z);
            if (horizontal.lengthSqr() > 1.0E-4D && lookHorizontal.lengthSqr() > 1.0E-4D
                    && horizontal.normalize().dot(lookHorizontal.normalize()) < -0.20D) {
                continue;
            }

            victim.invulnerableTime = 0;
            applyingBladeDamage = true;
            try {
                victim.hurt(damageSources().mobAttack(this), amount);
            } finally {
                applyingBladeDamage = false;
            }
            victim.invulnerableTime = 0;
        }
    }

    private void dashTowardBladeTarget() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        Vec3 delta = target.position().subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            Vec3 dash = horizontal.normalize().scale(1.45D);
            setDeltaMovement(dash.x, Math.max(getDeltaMovement().y, 0.10D), dash.z);
            hasImpulse = true;
        }
    }

    private void finishBladeTechnique(int cooldown) {
        entityData.set(DATA_BLADE_TECHNIQUE, BLADE_TECHNIQUE_NONE);
        entityData.set(DATA_BLADE_TECHNIQUE_TICK, 0);
        bladeTechniqueCooldown = cooldown;
    }

    private void ensureSlashBladeEquipped() {
        if (hasSlashBladeEquipped()) {
            return;
        }

        ItemStack blade = SlashBladeCompat.createRandomVenusZombieBlade(getRandom());
        if (!blade.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, blade);
        }
    }

    private void applyBladeReach() {
        AttributeInstance reach = getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null && reach.getBaseValue() < BLADE_INTERACTION_RANGE) {
            reach.setBaseValue(BLADE_INTERACTION_RANGE);
        }
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
        return SlashBladeCompat.isSlashBladeItem(living.getMainHandItem())
                || SlashBladeCompat.isSlashBladeItem(living.getOffhandItem());
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
            // Optional compatibility: VenusMod must still run when SlashBlade is absent.
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(NBT_SLASHBLADE_WIELDER, isSlashBladeWielder());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean(NBT_SLASHBLADE_WIELDER)) {
            setSlashBladeWielder(true);
        }
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
