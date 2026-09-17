package dev.ssscfw.venusmod.treasure;

/** 王の財宝1本ぶんの固定加算と刀種ボーナスだけを扱う。 */
public final class RoyalBladeDamageRules {
    public enum BladeGrade {
        NORMAL(0.0F),
        MARKED(2.0F),
        BEWITCHED(6.0F);

        private final float bonus;

        BladeGrade(float bonus) {
            this.bonus = bonus;
        }

        public float bonus() {
            return bonus;
        }
    }

    private RoyalBladeDamageRules() {}

    /** 妖刀は同時に印でもあるため、妖刀ボーナス+6だけを採用する。 */
    public static BladeGrade grade(boolean enchanted, boolean bewitched) {
        if (bewitched) return BladeGrade.BEWITCHED;
        if (enchanted) return BladeGrade.MARKED;
        return BladeGrade.NORMAL;
    }

    /** 直撃 = 5 + 刀のベースダメージ/エンチャント補正後 + 刀種ボーナス。 */
    public static float directDamage(float weaponDamageWithEnchantments, BladeGrade grade) {
        return 5.0F + weaponDamageWithEnchantments + grade.bonus();
    }

    /** 爆風 = 3 + 刀のベースダメージ/エンチャント補正後 + 刀種ボーナス。 */
    public static float blastDamage(float weaponDamageWithEnchantments, BladeGrade grade) {
        return 3.0F + weaponDamageWithEnchantments + grade.bonus();
    }
}
