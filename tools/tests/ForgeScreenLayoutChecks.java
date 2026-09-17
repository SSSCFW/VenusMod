import dev.ssscfw.venusmod.upgrade.ForgeScreenLayout;

/** スロット/素材行/強化ボタンの領域重なりと素材名の利用可能幅を検査する。 */
public final class ForgeScreenLayoutChecks {
    private static int checks;
    private static void check(boolean ok) {
        checks++;
        if (!ok) throw new AssertionError("Forge layout check " + checks);
    }
    public static void main(String[] args) {
        check(ForgeScreenLayout.WIDTH <= 320 && ForgeScreenLayout.HEIGHT <= 240);
        for (int i = 0; i < 4; i++) {
            int y = ForgeScreenLayout.costY(i);
            check(y >= 64 && y + 16 <= 140);
            if (i > 0) check(ForgeScreenLayout.costY(i - 1) + 16 < y);
        }
        check(ForgeScreenLayout.COST_RIGHT + 10 <= ForgeScreenLayout.INFO_X);
        check(ForgeScreenLayout.UPGRADE_Y + 18 <= 140);
        check(ForgeScreenLayout.INVENTORY_Y >= 154);
        check(ForgeScreenLayout.INVENTORY_Y + 2 * 18 + 16 < ForgeScreenLayout.HOTBAR_Y);
        check(ForgeScreenLayout.HOTBAR_Y + 17 < ForgeScreenLayout.HEIGHT);
        check(ForgeScreenLayout.INVENTORY_X + 8 * 18 + 17 < ForgeScreenLayout.WIDTH);
        for (int countWidth = 0; countWidth <= 110; countWidth++) {
            check(ForgeScreenLayout.COST_NAME_X + ForgeScreenLayout.nameWidth(countWidth) + 8
                    <= ForgeScreenLayout.COST_RIGHT - countWidth);
        }
        System.out.println("ForgeScreenLayoutChecks: " + checks + " passed");
    }
}
