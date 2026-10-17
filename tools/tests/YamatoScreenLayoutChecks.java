/** GUIスケール変更時にも地点行・ページ操作・サイドバーを画面内へ収める。 */
public final class YamatoScreenLayoutChecks {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static int number(Object target, String method) throws Exception {
        return ((Number)target.getClass().getMethod(method).invoke(target)).intValue();
    }

    public static void main(String[] args) throws Exception {
        Class<?> layout;
        try {
            layout = Class.forName("dev.ssscfw.venusmod.client.YamatoScreenLayout");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Dimension travel has no bounded responsive layout", missing);
        }
        int[][] sizes = {{320, 216}, {320, 240}, {384, 216}, {480, 270}, {480, 300}, {640, 360}, {960, 540}, {1920, 1080}};
        for (int[] size : sizes) {
            Object actual = layout.getMethod("forWindow", int.class, int.class).invoke(null, size[0], size[1]);
            int left = number(actual, "left"), top = number(actual, "top");
            int width = number(actual, "width"), height = number(actual, "height");
            int listX = number(actual, "listX"), listWidth = number(actual, "listWidth");
            int rowsY = number(actual, "rowsY"), rowCount = number(actual, "rowCount");
            int footerY = number(actual, "footerY");
            check(left >= 0 && top >= 0 && left + width <= size[0] && top + height <= size[1], "Frame outside window");
            check(listX >= left && listX + listWidth <= left + width, "List outside frame");
            check(listWidth >= 160, "Destination names lack a usable width");
            check(rowCount >= 2, "Small window cannot show two destinations");
            check(rowsY + rowCount * 36 <= footerY - 2, "Last row overlaps paging buttons");
            check(footerY + 20 <= top + height - 4, "Footer clipped");
            boolean sidebar = (Boolean)actual.getClass().getMethod("sidebar").invoke(actual);
            check(sidebar, "Left settings and save panel missing at " + size[0] + "x" + size[1]);
            int sideX = number(actual, "sideX"), sideWidth = number(actual, "sideWidth");
            check(sideX >= left && sideX + sideWidth + 6 <= listX, "Sidebar overlaps list");
            check(sideWidth >= 112 && sideWidth - 8 >= 104, "Sidebar controls are too narrow");
            int settingsY = number(actual, "settingsY"), step = number(actual, "settingsStep");
            int offset = number(actual, "settingsControlOffset");
            for (int row = 0; row < 3; row++) {
                int labelY = settingsY + row * step;
                int widgetY = labelY + offset;
                check(labelY >= top + 27 && labelY + 9 <= widgetY, "Setting label overlaps its control");
                check(widgetY + 20 <= top + height - 5, "Setting editor outside sidebar");
                if (row < 2) check(widgetY + 20 <= settingsY + (row + 1) * step, "Setting dropdown overlaps next label");
            }
            int applyY = number(actual, "applyY"), saveY = number(actual, "saveY"), folderY = number(actual, "folderY");
            check(settingsY + 2 * step + offset + 20 <= applyY, "Seconds editor overlaps Apply");
            check(applyY + 20 < saveY && saveY + 20 < folderY, "Sidebar action buttons overlap");
            check(folderY + 20 <= top + height - 5, "New folder button clipped");
            check(listWidth - 134 >= 26, "Folder breadcrumb cannot be operated");
            check(listWidth - 32 >= 128, "Destination row has no usable name area");
            check(listWidth - 50 >= 110, "Rift row has no usable summary area");
        }
        System.out.println("YamatoScreenLayoutChecks: " + checks + " passed");
    }
}
