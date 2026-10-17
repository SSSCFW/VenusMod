package dev.ssscfw.venusmod.client;

/** 最小GUIでも左の設定・保存操作を残し、幅と縦間隔を縮めて中央一覧と両立する。 */
public record YamatoScreenLayout(int left, int top, int width, int height, boolean sidebar,
                                 int sideX, int sideWidth, int listX, int listWidth,
                                 int rowsY, int rowCount, int footerY) {
    public static final int ROW_HEIGHT = 36;

    public static YamatoScreenLayout forWindow(int screenWidth, int screenHeight) {
        int width = Math.min(900, Math.max(1, screenWidth - 12));
        int height = Math.min(520, Math.max(1, screenHeight - 12));
        int left = (screenWidth - width) / 2;
        int top = (screenHeight - height) / 2;
        boolean sidebar = screenWidth >= 320 && screenHeight >= 216;
        int sideWidth = sidebar ? Math.min(168, Math.max(112, screenWidth * 7 / 20)) : 0;
        int listX = left + 6 + (sidebar ? sideWidth + 8 : 0);
        int listWidth = width - 12 - (sidebar ? sideWidth + 8 : 0);
        int rowsY = top + 100;
        int footerY = top + height - 24;
        int rowCount = Math.max(1, (footerY - rowsY - 2) / ROW_HEIGHT);
        return new YamatoScreenLayout(left, top, width, height, sidebar, left + 6, sideWidth,
                listX, listWidth, rowsY, rowCount, footerY);
    }

    public int settingsY() {
        return top + (height < 288 ? 29 : 30);
    }

    public int tabX(int tab) {
        return listX + tab * ((listWidth - 8) / 3 + 4);
    }

    public int tabWidth(int tab) {
        int firstWidth = (listWidth - 8) / 3;
        return tab == 2 ? listWidth - 2 * (firstWidth + 4) : firstWidth;
    }

    public int selectionY() {
        return top + 76;
    }

    public int settingsStep() {
        return height < 256 ? 27 : height < 312 ? 32 : 40;
    }

    public int settingsControlOffset() {
        return settingsStep() == 27 ? 10 : settingsStep() == 32 ? 11 : 12;
    }

    public int settingsControlHeight() {
        return settingsStep() == 27 ? 17 : 20;
    }

    public int actionHeight() {
        return settingsStep() == 27 ? 18 : 20;
    }

    public int saveY() {
        // 「設定を適用」ボタンを廃止したため、設定の次には直接保存操作を置く。
        return settingsY() + settingsStep() * 4 + 2;
    }

    public int folderY() {
        return saveY() + actionHeight() + (settingsStep() == 27 ? 3 : 6);
    }
}
