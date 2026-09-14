package dev.ssscfw.venusmod.upgrade;

/** Minecraft本体に依存しない抜刀剣強化の上限・効果量・素材数計算。 */
public final class BladeUpgradeRules {
    public enum Category {
        BLADE_POWER,
        SUMMONED_SWORD,
        CONCENTRATION,
        DURABILITY,
        VENUS_SLAYER
    }

    public record Cost(int pressureAlloy, int nickel, int venesite, int sulfur, boolean soulStoneRequired) {
        public Cost {
            if (pressureAlloy < 0 || nickel < 0 || venesite < 0 || sulfur < 0) {
                throw new IllegalArgumentException("Negative upgrade cost");
            }
        }
    }

    private BladeUpgradeRules() {}

    public static int maxLevel(Category category) {
        return switch (category) {
            case BLADE_POWER, SUMMONED_SWORD -> 10;
            case CONCENTRATION, DURABILITY, VENUS_SLAYER -> 5;
        };
    }

    public static Cost cost(Category category, int nextLevel) {
        int max = maxLevel(category);
        if (nextLevel < 1 || nextLevel > max) {
            throw new IllegalArgumentException("nextLevel outside 1.." + max + ": " + nextLevel);
        }
        int n = nextLevel;
        return switch (category) {
            case BLADE_POWER -> new Cost(
                    2 * n + (n * (n - 1)) / 4,
                    3 * n,
                    Math.max(0, n - 3),
                    (n + 1) / 2,
                    n >= 6);
            case SUMMONED_SWORD -> new Cost(
                    Math.max(0, n - 2),
                    n,
                    2 * n + (n * (n - 1)) / 5,
                    2 * n,
                    n >= 6);
            case CONCENTRATION -> new Cost(
                    n,
                    n,
                    2 * n,
                    3 * n,
                    n >= 4);
            case DURABILITY -> new Cost(
                    3 * n,
                    4 * n,
                    Math.max(0, n - 2),
                    2 * n,
                    n >= 4);
            case VENUS_SLAYER -> new Cost(
                    3 * n,
                    0,
                    4 * n,
                    3 * n,
                    n >= 3);
        };
    }

    /** 直接斬撃に加算する生ダメージ。Lv10で+15。 */
    public static float bladePowerBonus(int level) {
        return 1.5F * clampLevel(Category.BLADE_POWER, level);
    }

    /** 幻影剣1ヒットに加算する生ダメージ。Lv10で+10。 */
    public static float summonedSwordBonus(int level) {
        return 1.0F * clampLevel(Category.SUMMONED_SWORD, level);
    }

    /** SlashBlade本来の集中ランク獲得量に加える割合。Lv5で+50%。 */
    public static double concentrationBonusMultiplier(int level) {
        return 0.10D * clampLevel(Category.CONCENTRATION, level);
    }

    /** 耐久消費軽減率の分子。分母10、Lv5で50%。 */
    public static int durabilityReductionNumerator(int level) {
        return clampLevel(Category.DURABILITY, level);
    }

    /** 金星内での最終ダメージ倍率。Lv5で1.25倍。 */
    public static float venusDamageMultiplier(int level) {
        return 1.0F + 0.05F * clampLevel(Category.VENUS_SLAYER, level);
    }

    public static int clampLevel(Category category, int level) {
        return Math.max(0, Math.min(maxLevel(category), level));
    }
}
