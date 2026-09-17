import dev.ssscfw.venusmod.treasure.RoyalBladeDamageRules;

/** 王の財宝の直撃/爆風と印・妖刀ボーナスをMinecraftなしで検査する。 */
public final class RoyalBladeDamageRulesChecks {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static boolean eq(float a, float b) {
        return Math.abs(a - b) < 1.0E-6F;
    }

    public static void main(String[] args) {
        check(RoyalBladeDamageRules.grade(false, false) == RoyalBladeDamageRules.BladeGrade.NORMAL,
                "通常刀");
        check(RoyalBladeDamageRules.grade(true, false) == RoyalBladeDamageRules.BladeGrade.MARKED,
                "印は+2種別");
        check(RoyalBladeDamageRules.grade(true, true) == RoyalBladeDamageRules.BladeGrade.BEWITCHED,
                "妖刀は印より優先");

        check(eq(RoyalBladeDamageRules.directDamage(4.0F, RoyalBladeDamageRules.BladeGrade.NORMAL), 9.0F),
                "直撃=5+刀ダメージ");
        check(eq(RoyalBladeDamageRules.blastDamage(4.0F, RoyalBladeDamageRules.BladeGrade.NORMAL), 7.0F),
                "爆風=3+刀ダメージ");
        check(eq(RoyalBladeDamageRules.directDamage(5.5F, RoyalBladeDamageRules.BladeGrade.MARKED), 12.5F),
                "印の直撃+2");
        check(eq(RoyalBladeDamageRules.blastDamage(5.5F, RoyalBladeDamageRules.BladeGrade.MARKED), 10.5F),
                "印の爆風+2");
        check(eq(RoyalBladeDamageRules.directDamage(5.5F, RoyalBladeDamageRules.BladeGrade.BEWITCHED), 16.5F),
                "妖刀の直撃+6");
        check(eq(RoyalBladeDamageRules.blastDamage(5.5F, RoyalBladeDamageRules.BladeGrade.BEWITCHED), 14.5F),
                "妖刀の爆風+6");
        check(RoyalBladeDamageRules.BladeGrade.BEWITCHED.bonus() == 6.0F,
                "妖刀は印+2と妖刀+6を重複させない");

        System.out.println("RoyalBladeDamageRulesChecks: " + checks + " passed");
    }
}
