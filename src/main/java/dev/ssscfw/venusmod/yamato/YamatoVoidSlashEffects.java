package dev.ssscfw.venusmod.yamato;

import com.mojang.logging.LogUtils;
import dev.ssscfw.venusmod.compat.YamatoCompat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * 閻魔刀コマンド発動時の、SlashBlade標準Void Slashだけを拡張する。
 *
 * 既存斬撃Entityが捕捉した対象を読むため、範囲判定は元Modと完全に共有する。
 * 最初に捕捉されたMobへ0.5ダメージを与え、斬撃Entityが二段目の納刀ダメージを
 * 与えて破棄されるまではEntityTickEvent.PreをキャンセルしてMobの全tickを止める。
 * クリーパーの爆発カウント、AI攻撃、移動、状態変化も進行しない。
 */
public final class YamatoVoidSlashEffects {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float INITIAL_DAMAGE = 0.5F;
    private static final int EXPECTED_NATIVE_LIFETIME = 36;
    private static final int MAX_SPAWN_DELAY = 30;
    private static final int RELEASE_GRACE = 5;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, SlashSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Mob> FROZEN = new HashMap<>();
    private static final YamatoVoidSlashFreezeLedger FREEZE = new YamatoVoidSlashFreezeLedger();

    private static boolean resolved;
    private static boolean warned;
    private static Class<?> nativeSlashClass;
    private static Method nativeHits;
    private static Method nativeLifetime;
    private static Method nativeDamage;
    private static Method clearNativeStun;

    private record Pending(long tick) {}

    private static final class SlashSession {
        final Projectile effect;
        final UUID owner;
        final long spawnedAt;
        final int lifetime;
        final Set<UUID> targets = new HashSet<>();

        SlashSession(Projectile effect, UUID owner, long spawnedAt, int lifetime) {
            this.effect = effect;
            this.owner = owner;
            this.spawnedAt = spawnedAt;
            this.lifetime = lifetime;
        }
    }

    private YamatoVoidSlashEffects() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onEntityPre);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onEntityPost);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onServerTick);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onStartTracking);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onLogout);
        NeoForge.EVENT_BUS.addListener(YamatoVoidSlashEffects::onServerStopped);
    }

    /** 閻魔刀の内部識別・サーバー入力検証・ComboState遷移に成功した後だけ呼ばれる。 */
    public static void begin(ServerPlayer player) {
        PENDING.put(player.getUUID(), new Pending(player.server.overworld().getGameTime()));
    }

    /**
     * 元Modの時空裂閃だけを捕捉。プレイヤーが通常の右クリックで出した斬撃は対象外。
     * SlashBladeは任意依存なので、本体クラスは一切直接リンクしない。
     */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile effect)
                || !(effect.getOwner() instanceof ServerPlayer player)) return;

        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        long now = player.server.overworld().getGameTime();
        if (now < pending.tick() || now - pending.tick() > MAX_SPAWN_DELAY
                || player.level() != effect.level() || !YamatoCompat.isVoidSlashActive(player)
                || !resolveNativeSlash() || !nativeSlashClass.isInstance(effect)) return;

        try {
            int lifetime = ((Number) nativeLifetime.invoke(effect)).intValue();
            double damage = ((Number) nativeDamage.invoke(effect)).doubleValue();
            // 元ModのVoid Slashは36tickの無ダメージ斬撃で、納刀時に遅延ダメージを与える。
            if (lifetime != EXPECTED_NATIVE_LIFETIME || damage != 0.0D) return;
            PENDING.remove(player.getUUID());
            SESSIONS.put(effect.getUUID(), new SlashSession(effect, player.getUUID(), now, lifetime));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warnOnce("時空裂閃Entityの識別に失敗しました", exception);
        }
    }

    /** 停止中のMobの全tickを省略する。速度0だけでは爆発・AI攻撃が進むため必須。 */
    private static void onEntityPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity instanceof Mob mob
                && FROZEN.get(mob.getUUID()) == mob && FREEZE.isHeld(mob.getUUID())) {
            mob.setDeltaMovement(Vec3.ZERO);
            event.setCanceled(true);
        }
    }

    /** 標準斬撃の捕捉リストへ追加された対象だけに、一度だけ初撃を与えて拘束する。 */
    private static void onEntityPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        SlashSession session = SESSIONS.get(entity.getUUID());
        if (session == null || session.effect != entity) return;

        // 元Modの後段攻撃はEntitySlashEffect.tryDespawn()の最後で発動してからremoveする。
        // Postで解除すれば後段攻撃を止めず、攻撃後に必ずMobの動作を再開できる。
        if (entity.isRemoved() || entity.tickCount > session.lifetime) {
            finish(entity.getUUID());
            return;
        }

        if (!(session.effect.getOwner() instanceof ServerPlayer attacker) || !attacker.isAlive()) {
            finish(entity.getUUID());
            return;
        }
        try {
            Object rawHits = nativeHits.invoke(entity);
            if (!(rawHits instanceof List<?> hits)) return;
            for (Object hit : List.copyOf(hits)) {
                if (!(hit instanceof Mob target) || !target.isAlive()
                        || target.level() != entity.level()
                        || !session.targets.add(target.getUUID())) continue;

                // 元Mod側の捕捉判定を変更しない。0.5は強化や基礎攻撃力に比例しない固定値。
                // 一段目にも命中した実感を与えるため、標準の無敵時間を解除してから適用。
                target.invulnerableTime = 0;
                target.hurt(attacker.damageSources().playerAttack(attacker), INITIAL_DAMAGE);
                if (!target.isAlive() || target.isRemoved()) continue;

                target.setDeltaMovement(Vec3.ZERO);
                FROZEN.put(target.getUUID(), target);
                if (FREEZE.hold(target.getUUID(), entity.getUUID())) {
                    YamatoNetwork.sendFrozen(target, true);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warnOnce("時空裂閃の捕捉対象を取得できませんでした", exception);
            finish(entity.getUUID());
        }
    }

    /** エフェクトが別の処理で破棄された場合にも拘束を残さない。 */
    private static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        UUID id = event.getEntity().getUUID();
        if (SESSIONS.containsKey(id)) finish(id);
        if (event.getEntity() instanceof Mob && FROZEN.get(id) == event.getEntity()) {
            FROZEN.remove(id);
            FREEZE.forgetTarget(id);
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Mob mob
                && FREEZE.isHeld(mob.getUUID()) && FROZEN.get(mob.getUUID()) == mob) {
            YamatoNetwork.sendFrozen(player, mob.getId(), true);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PENDING.remove(player.getUUID());
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            SlashSession session = SESSIONS.get(id);
            if (session != null && session.owner.equals(player.getUUID())) finish(id);
        }
    }

    /** 斬撃のアンロード等でPost/Leaveが呼ばれないときにも安全に解放する。 */
    private static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        PENDING.entrySet().removeIf(entry -> now < entry.getValue().tick()
                || now - entry.getValue().tick() > MAX_SPAWN_DELAY);
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            SlashSession session = SESSIONS.get(id);
            if (session != null && (session.effect.isRemoved()
                    || now < session.spawnedAt
                    || now - session.spawnedAt > session.lifetime + RELEASE_GRACE)) finish(id);
        }
        for (UUID id : new ArrayList<>(FROZEN.keySet())) {
            Mob mob = FROZEN.get(id);
            if (mob != null && (!mob.isAlive() || mob.isRemoved())) {
                FROZEN.remove(id);
                FREEZE.forgetTarget(id);
            }
        }
    }

    private static void finish(UUID effectId) {
        SlashSession session = SESSIONS.remove(effectId);
        if (session == null) return;
        for (UUID targetId : session.targets) {
            if (!FREEZE.release(targetId, effectId)) continue;
            Mob mob = FROZEN.remove(targetId);
            if (mob == null || mob.isRemoved()) continue;
            // Void Slash標準のStunManagerも同時に解除する。標準の40tickスタンを
            // 納刀攻撃後に残すと、拘束が数tick延びてしまう。
            clearStun(mob);
            YamatoNetwork.sendFrozen(mob, false);
        }
    }

    private static boolean resolveNativeSlash() {
        if (resolved) return nativeSlashClass != null;
        resolved = true;
        try {
            nativeSlashClass = Class.forName("mods.flammpfeil.slashblade.entity.EntitySlashEffect");
            nativeHits = nativeSlashClass.getMethod("getAlreadyHits");
            nativeLifetime = nativeSlashClass.getMethod("getLifetime");
            nativeDamage = nativeSlashClass.getMethod("getDamage");
            Class<?> stun = Class.forName("mods.flammpfeil.slashblade.ability.StunManager");
            clearNativeStun = stun.getMethod("removeStun", net.minecraft.world.entity.LivingEntity.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            nativeSlashClass = null;
            warnOnce("SlashBladeの時空裂閃互換APIを初期化できませんでした", exception);
            return false;
        }
    }

    private static void clearStun(Mob target) {
        if (clearNativeStun == null) return;
        try {
            clearNativeStun.invoke(null, target);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warnOnce("納刀後のスタン解除に失敗しました", exception);
        }
    }

    private static void warnOnce(String message, Throwable error) {
        if (warned) return;
        warned = true;
        LOGGER.warn(message, error);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        SESSIONS.clear();
        FROZEN.clear();
        FREEZE.clear();
    }
}
