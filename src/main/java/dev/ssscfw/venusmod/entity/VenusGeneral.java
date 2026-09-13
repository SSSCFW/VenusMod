package dev.ssscfw.venusmod.entity;

import dev.ssscfw.venusmod.compat.SlashBladeCompat;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 第二ボス「金星将軍」。前セッション仕様の居合・連続斬り・幻影剣・ガードと3段階フェーズを実装。
 */
public final class VenusGeneral extends VenusZombie {
    private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(1.0F, 0.18F, 0.55F), 1.0F);
    private final ServerBossEvent boss = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent postureBoss = new ServerBossEvent(Component.translatable("bossbar.venusmod.general_posture"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

    private int posture = BossCombatRules.GENERAL_MAX_POSTURE;
    private int postureBrokenTicks;
    private int iaiCooldown = 80;
    private int phantomCooldown = 120;
    private int behindCooldown = 100;
    private int guardCooldown = 80;
    private int guardTicks;
    private int lastPhase = 1;

    public VenusGeneral(EntityType<? extends net.minecraft.world.entity.monster.Zombie> type, Level level) {
        super(type, level);
        normalizeBossAttributes();
        xpReward = 350;
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setSlashBladeWielder(true);
        equipGeneralBlade();
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        setDropChance(EquipmentSlot.HEAD, 0.0F);
        setDropChance(EquipmentSlot.CHEST, 0.0F);
        setDropChance(EquipmentSlot.LEGS, 0.0F);
        setDropChance(EquipmentSlot.FEET, 0.0F);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return net.minecraft.world.entity.monster.Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 800.0D)
                .add(Attributes.ATTACK_DAMAGE, 24.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.90D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    private void normalizeBossAttributes() {
        setBase(Attributes.MAX_HEALTH, 800.0D);
        setBase(Attributes.ATTACK_DAMAGE, 24.0D);
        setBase(Attributes.ARMOR, 18.0D);
        setBase(Attributes.ARMOR_TOUGHNESS, 8.0D);
        setBase(Attributes.MOVEMENT_SPEED, 0.30D);
        setBase(Attributes.KNOCKBACK_RESISTANCE, 0.90D);
        setBase(Attributes.FOLLOW_RANGE, 48.0D);
        setHealth(getMaxHealth());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private void equipGeneralBlade() {
        ItemStack blade = SlashBladeCompat.createVenusGeneralBlade();
        if (!blade.isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, blade);
        else setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean canAttack(LivingEntity target) {
        return level().getDifficulty() != Difficulty.PEACEFUL && super.canAttack(target);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        boss.addPlayer(player);
        if (currentPhase() >= 3) postureBoss.addPlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        boss.removePlayer(player);
        postureBoss.removePlayer(player);
    }

    @Override public void die(DamageSource source) {
        boss.removeAllPlayers();
        postureBoss.removeAllPlayers();
        super.die(source);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        boss.setName(getDisplayName());
        boss.setProgress(Math.max(0.0F, getHealth() / getMaxHealth()));
        if (!isAlive() || server.getDifficulty() == Difficulty.PEACEFUL) {
            setTarget(null);
            return;
        }

        int phase = currentPhase();
        if (phase != lastPhase) {
            onPhaseChanged(server, phase);
            lastPhase = phase;
        }
        updatePhaseSpeed(phase);
        updatePostureBar(phase);

        if (iaiCooldown > 0) iaiCooldown--;
        if (phantomCooldown > 0) phantomCooldown--;
        if (behindCooldown > 0) behindCooldown--;
        if (guardCooldown > 0) guardCooldown--;
        if (guardTicks > 0) guardTicks--;
        if (postureBrokenTicks > 0) {
            postureBrokenTicks--;
            if (postureBrokenTicks == 0) posture = BossCombatRules.GENERAL_MAX_POSTURE;
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        double distance = distanceTo(target);

        if (guardCooldown <= 0 && distance < 8.0D) {
            guardTicks = phase >= 2 ? 26 : 20;
            guardCooldown = phase >= 3 ? 70 : 100;
            playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F);
        }
        if (phase >= 2 && behindCooldown <= 0 && distance < 18.0D) {
            teleportBehind(target, server);
            behindCooldown = phase >= 3 ? 65 : 100;
        }
        if (iaiCooldown <= 0 && distance >= 3.0D && distance <= 22.0D) {
            performIai(target, server, phase);
            iaiCooldown = phase >= 3 ? 55 : phase == 2 ? 75 : 100;
        } else if (!isBladeTechniqueActive() && getBladeTechniqueCooldown() <= 0 && distance <= 8.0D) {
            if (phase >= 2 && getRandom().nextBoolean()) startRapidSlash(target);
            else startBladeCombo(target);
        }
        if (phantomCooldown <= 0 && distance <= 28.0D) {
            performPhantomSwords(target, server, phase >= 3 ? 8 : 4);
            phantomCooldown = phase >= 3 ? 70 : phase == 2 ? 105 : 145;
        }
    }

    private int currentPhase() { return BossCombatRules.generalPhase(getHealth(), getMaxHealth()); }

    private void onPhaseChanged(ServerLevel server, int phase) {
        playSound(SoundEvents.WITHER_SPAWN, 1.0F, phase == 3 ? 1.35F : 1.65F);
        server.sendParticles(MAGENTA, getX(), getY() + 1.0D, getZ(), 80, 0.8D, 1.1D, 0.8D, 0.05D);
        if (phase >= 3) {
            posture = BossCombatRules.GENERAL_MAX_POSTURE;
            for (ServerPlayer player : boss.getPlayers()) postureBoss.addPlayer(player);
        }
    }

    private void updatePhaseSpeed(int phase) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(phase >= 3 ? 0.40D : phase == 2 ? 0.35D : 0.30D);
    }

    private void updatePostureBar(int phase) {
        if (phase < 3) {
            postureBoss.removeAllPlayers();
            return;
        }
        postureBoss.setName(postureBrokenTicks > 0
                ? Component.translatable("bossbar.venusmod.general_posture_broken")
                : Component.translatable("bossbar.venusmod.general_posture"));
        postureBoss.setProgress(postureBrokenTicks > 0 ? 0.0F : posture / (float) BossCombatRules.GENERAL_MAX_POSTURE);
    }

    private void teleportBehind(LivingEntity target, ServerLevel server) {
        Vec3 facing = target.getLookAngle();
        Vec3 horizontal = new Vec3(facing.x, 0.0D, facing.z);
        if (horizontal.lengthSqr() < 1.0E-4D) return;
        Vec3 destination = target.position().subtract(horizontal.normalize().scale(2.2D));
        Vec3 delta = destination.subtract(position());
        AABB moved = getBoundingBox().move(delta.x, delta.y, delta.z);
        if (!server.noCollision(this, moved)) return;
        setPos(destination.x, destination.y, destination.z);
        getLookControl().setLookAt(target, 180.0F, 180.0F);
        server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(), 28, 0.35D, 0.7D, 0.35D, 0.12D);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.4F);
    }

    private void performIai(LivingEntity target, ServerLevel server, int phase) {
        Vec3 delta = target.position().subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            Vec3 dash = horizontal.normalize().scale(Math.min(3.0D, Math.sqrt(horizontal.lengthSqr()) * 0.65D));
            setDeltaMovement(dash.x, 0.12D, dash.z);
            hasImpulse = true;
        }
        SlashBladeCompat.doSlash(this, -18.0F, false, true, phase >= 3 ? 1.8D : 1.45D);
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0D, target.getZ(), 5, 0.4D, 0.5D, 0.4D, 0.0D);
        target.invulnerableTime = 0;
        target.hurt(damageSources().mobAttack(this), phase >= 3 ? 32.0F : phase == 2 ? 28.0F : 24.0F);
        target.invulnerableTime = 0;
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
    }

    private void performPhantomSwords(LivingEntity target, ServerLevel server, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double x = getX() + Math.cos(angle) * 2.2D;
            double z = getZ() + Math.sin(angle) * 2.2D;
            server.sendParticles(MAGENTA, x, getY() + 1.4D, z, 8, 0.08D, 0.25D, 0.08D, 0.0D);
            SlashBladeCompat.doSlash(this, (float) Math.toDegrees(angle), true, false, 0.35D);
        }
        for (int step = 0; step <= 12; step++) {
            double t = step / 12.0D;
            double x = MthLerp(getX(), target.getX(), t);
            double y = MthLerp(getY() + 1.3D, target.getY() + 1.0D, t);
            double z = MthLerp(getZ(), target.getZ(), t);
            server.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        target.invulnerableTime = 0;
        target.hurt(damageSources().mobAttack(this), count == 8 ? 24.0F : 12.0F);
        target.invulnerableTime = 0;
        playSound(SoundEvents.TRIDENT_THROW.value(), 1.1F, count == 8 ? 0.75F : 1.0F);
    }

    private static double MthLerp(double from, double to, double t) { return from + (to - from) * t; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        int phase = currentPhase();
        boolean postureProtected = phase >= 3 && posture > 0 && postureBrokenTicks <= 0;
        float raw = amount;
        if (postureProtected) {
            int next = BossCombatRules.postureAfterHit(posture, raw);
            if (next == 0 && posture > 0) {
                posture = 0;
                postureBrokenTicks = 100;
                playSound(SoundEvents.ITEM_BREAK, 1.6F, 0.5F);
            } else posture = next;
        }
        float adjusted = BossCombatRules.generalDamage(raw, phase, postureProtected ? Math.max(1, posture) : 0, guardTicks > 0);
        return super.hurt(source, adjusted);
    }

    @Override public void knockback(double strength, double x, double z) {
        if (postureBrokenTicks > 0) super.knockback(strength * 0.6D, x, z);
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return VenusDimensionContent.GENERAL.get().getDefaultLootTable();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GeneralPosture", posture);
        tag.putInt("GeneralPostureBroken", postureBrokenTicks);
        tag.putInt("GeneralIai", iaiCooldown);
        tag.putInt("GeneralPhantom", phantomCooldown);
        tag.putInt("GeneralBehind", behindCooldown);
        tag.putInt("GeneralGuard", guardCooldown);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        posture = Math.clamp(tag.getInt("GeneralPosture"), 0, BossCombatRules.GENERAL_MAX_POSTURE);
        if (!tag.contains("GeneralPosture")) posture = BossCombatRules.GENERAL_MAX_POSTURE;
        postureBrokenTicks = Math.max(0, tag.getInt("GeneralPostureBroken"));
        iaiCooldown = Math.max(0, tag.getInt("GeneralIai"));
        phantomCooldown = Math.max(0, tag.getInt("GeneralPhantom"));
        behindCooldown = Math.max(0, tag.getInt("GeneralBehind"));
        guardCooldown = Math.max(0, tag.getInt("GeneralGuard"));
        setPersistenceRequired();
        equipGeneralBlade();
    }
}
