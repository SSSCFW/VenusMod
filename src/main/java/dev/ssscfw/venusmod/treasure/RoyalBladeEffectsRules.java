package dev.ssscfw.venusmod.treasure;

/** 火属性・耐久力・壊れた幻想と3状態保護のMinecraft非依存規則。 */
public final class RoyalBladeEffectsRules {
    public static final int NORMAL = 0;
    public static final int FAVORITE = 1;
    public static final int PHANTASM_PROTECTED = 2;
    private RoyalBladeEffectsRules() {}

    public static int enchantmentLevel(int level) { return Math.max(0, Math.min(255, level)); }
    public static int fireSeconds(int level) { return 4 * enchantmentLevel(level); }

    /** 刀は道具扱い。耐久力Lvに対し消費確率は1/(Lv+1)。抽選は射出完了ごとに1回だけ。 */
    public static boolean usesDurability(int level, int roll) {
        int safe = enchantmentLevel(level);
        if (roll < 0 || roll > safe) throw new IllegalArgumentException("Durability roll outside bound");
        return roll == 0;
    }

    public static float damage(float normal, boolean phantasm) {
        return Float.isFinite(normal) && normal > 0 ? normal * (phantasm ? 5.0F : 1.0F) : 0.0F;
    }
    public static float blastRadius(boolean phantasm) { return phantasm ? 6.0F : 2.0F; }
    public static int nextProtection(int current) { return current >= NORMAL && current <= PHANTASM_PROTECTED ? (current + 1) % 3 : NORMAL; }
    public static boolean eligible(int protection, boolean broken, boolean phantasm) {
        return !broken && protection != FAVORITE && (!phantasm || protection != PHANTASM_PROTECTED);
    }
    public static boolean returnsBlade(boolean phantasm, boolean impacted) { return !phantasm || !impacted; }
}
