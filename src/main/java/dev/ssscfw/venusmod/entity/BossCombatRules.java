package dev.ssscfw.venusmod.entity;

/**
 * ボス戦の数値ロジックをMinecraft本体から分離して回帰テストできるようにしたもの。
 */
public final class BossCombatRules {
    public static final int GENERAL_MAX_POSTURE = 100;

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

    /** 圧力コアが1個残るごとに15%軽減。3個で45%軽減。 */
    public static float aphroditeDamage(float amount, int pressureCores) {
        int cores = Math.clamp(pressureCores, 0, 3);
        return Math.max(0.0F, amount) * (1.0F - cores * 0.15F);
    }

    /** 128RPM以上を5秒維持するとシールド解除。停止時は2倍速で放電する。 */
    public static int controllerCharge(int current, float rpm, boolean createInstalled, boolean fallbackRedstone) {
        int charge = Math.clamp(current, 0, 100);
        boolean powered = createInstalled ? Float.isFinite(rpm) && Math.abs(rpm) >= 128.0F : fallbackRedstone;
        return powered ? Math.min(100, charge + 1) : Math.max(0, charge - 2);
    }
}
