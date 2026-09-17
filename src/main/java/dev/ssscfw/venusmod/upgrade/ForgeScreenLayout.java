package dev.ssscfw.venusmod.upgrade;

/** Menu/Screen共通の座標。幅の狭い2列の素材名表示をやめ、アイコン付き4行へ分離する。 */
public final class ForgeScreenLayout {
    public static final int WIDTH = 300;
    public static final int HEIGHT = 236;
    public static final int BLADE_X = 18;
    public static final int BLADE_Y = 28;
    public static final int INVENTORY_X = 69;
    public static final int INVENTORY_Y = 154;
    public static final int HOTBAR_Y = 212;
    public static final int COST_X = 14;
    public static final int COST_NAME_X = 34;
    public static final int COST_RIGHT = 164;
    public static final int INFO_X = 174;
    public static final int INFO_RIGHT = 292;
    public static final int UPGRADE_Y = 114;

    private ForgeScreenLayout() {}

    public static int costY(int row) {
        if (row < 0 || row > 3) throw new IllegalArgumentException("Material row outside 0..3");
        return 64 + 18 * row;
    }

    public static int nameWidth(int countWidth) {
        return Math.max(0, COST_RIGHT - COST_NAME_X - 8 - Math.max(0, countWidth));
    }
}
