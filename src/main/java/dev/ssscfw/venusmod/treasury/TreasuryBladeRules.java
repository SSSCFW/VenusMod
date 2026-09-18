package dev.ssscfw.venusmod.treasury;

/** 刀自身の保護・破損時消滅・低ランク優先のMinecraft非依存規則。 */
public final class TreasuryBladeRules {
    private TreasuryBladeRules() {}

    /** 刀に設定があればそれを正とし、なければ旧SavedDataの設定を移行する。 */
    public static int protection(int stored, boolean oldFavorite, boolean oldPhantasmProtected) {
        if (stored >= 0 && stored <= 2) return stored;
        return oldFavorite ? 1 : oldPhantasmProtected ? 2 : 0;
    }

    public static boolean vanishesOnBreak(boolean destructable, boolean broken) {
        return destructable && broken;
    }

    /** 消滅型の通常刀→通常刀→印→妖刀。名前や言語に依存せずSlashBladeの状態で分類する。 */
    public static int rank(boolean destructable, boolean marked, boolean bewitched) {
        return bewitched ? 3 : marked ? 2 : destructable ? 0 : 1;
    }
}
