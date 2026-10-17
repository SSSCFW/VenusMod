package dev.ssscfw.venusmod.entity;

/**
 * ボス戦の数値ロジックをMinecraft本体から分離して回帰テストできるようにしたもの。
 */
public final class BossCombatRules {
    public static final int GENERAL_MAX_POSTURE = 100;
    public static final float APHRODITE_SHIELD_CAPACITY = 200.0F;
    public static final int APHRODITE_SHIELD_RECOVERY_TICKS = 20 * 10;

    private BossCombatRules() {}

    public static int generalPhase(float health, float maxHealth) {
        if (!(maxHealth > 0.0F)) return 1;
        float ratio = Math.max(0.0F, health) / maxHealth;
        if (ratio <= 0.35F) return 3;
        if (ratio <= 0.70F) return 2;
        return 1;
    }

    /** Phase3は姿勢を崩すまで被ダメージ50%。ガード中はさらに35%。 */
    public static float generalDamage(float amount, int phase, int posture, boolean guarding) {
        float result = Math.max(0.0F, amount);
        if (phase >= 3 && posture > 0) result *= 0.5F;
        if (guarding) result *= 0.35F;
        return result;
    }

    public static int postureAfterHit(int posture, float rawDamage) {
        if (posture <= 0) return 0;
        int loss = Math.max(1, Math.round(Math.max(0.0F, rawDamage) * 2.0F));
        return Math.max(0, posture - loss);
    }

    /**
     * アフロディーテの自己完結型シールド。0になった瞬間から200tick後に全回復。
     * 破壊した攻撃の余剰ダメージは本体へ貫通しない。
     */
    public record AphroditeShield(float durability, int recoveryTicks) {
        public AphroditeShield {
            durability = Float.isFinite(durability)
                    ? Math.clamp(durability, 0.0F, APHRODITE_SHIELD_CAPACITY)
                    : APHRODITE_SHIELD_CAPACITY;
            recoveryTicks = durability > 0.0F ? 0 : Math.clamp(recoveryTicks, 0, APHRODITE_SHIELD_RECOVERY_TICKS);
        }

        public static AphroditeShield full() {
            return new AphroditeShield(APHRODITE_SHIELD_CAPACITY, 0);
        }

        public boolean active() {
            return durability > 0.0F;
        }

        public AphroditeShield absorb(float damage) {
            if (!active() || !Float.isFinite(damage) || damage <= 0.0F) return this;
            float remaining = Math.max(0.0F, durability - damage);
            return new AphroditeShield(remaining, remaining > 0.0F ? 0 : APHRODITE_SHIELD_RECOVERY_TICKS);
        }

        public AphroditeShield tick() {
            if (active()) return this;
            return recoveryTicks <= 1 ? full() : new AphroditeShield(0.0F, recoveryTicks - 1);
        }
    }

    /** 旧バージョン用の計算。新しいアフロディーテ戦では使用しない。 */

    public static float aphroditeDamage(float amount, int pressureCores) {
        int cores = Math.clamp(pressureCores, 0, 3);
        return Math.max(0.0F, amount) * (1.0F - cores * 0.15F);
    }

    /** 旧制御ブロックの動作を保つ互換処理。ボスのシールドには影響しない。 */
    public static int controllerCharge(int current, float rpm, boolean createInstalled, boolean fallbackRedstone) {
        int charge = Math.clamp(current, 0, 100);
        boolean powered = createInstalled ? Float.isFinite(rpm) && Math.abs(rpm) >= 128.0F : fallbackRedstone;
        return powered ? Math.min(100, charge + 1) : Math.max(0, charge - 2);
    }
}
