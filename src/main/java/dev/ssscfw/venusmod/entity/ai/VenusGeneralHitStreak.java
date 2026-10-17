package dev.ssscfw.venusmod.entity.ai;

import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * 金星将軍の反撃用の連続被弾判定。
 * 同じ攻撃者から60tick以内に受けた有効ダメージだけを数える。
 * 閾値は連撃開始時に8～15回から一度だけ抽選する。
 */
public final class VenusGeneralHitStreak {
    public static final int MIN_HITS = 8;
    public static final int MAX_HITS = 15;
    public static final int MAX_GAP_TICKS = 60;

    private UUID attackerId;
    private long lastHitTick;
    private int hitCount;
    private int threshold;

    /** テレポートが必要な回数に達したらtrue。安全な移動先がなければ次の被弾時に再試行する。 */
    public boolean recordHit(UUID attacker, long tick, IntSupplier newThreshold) {
        if (attacker == null) {
            return false;
        }
        if (attackerId == null || !attackerId.equals(attacker)
                || tick < lastHitTick || tick - lastHitTick > MAX_GAP_TICKS) {
            hitCount = 0;
            attackerId = attacker;
            threshold = Math.clamp(newThreshold.getAsInt(), MIN_HITS, MAX_HITS);
        }

        lastHitTick = tick;
        hitCount++;
        return hitCount >= threshold;
    }

    public void reset() {
        attackerId = null;
        hitCount = 0;
        threshold = 0;
        lastHitTick = 0;
    }
}
