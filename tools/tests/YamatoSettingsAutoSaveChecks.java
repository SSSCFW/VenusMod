import java.nio.file.Files;
import java.nio.file.Path;

/** 設定の自動保存と転送時のアトミック反映を、ビルドのたびに配線チェックする。 */
public final class YamatoSettingsAutoSaveChecks {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/java/dev/ssscfw/venusmod");
        String screen = Files.readString(root.resolve("client/YamatoTravelScreen.java"));
        String network = Files.readString(root.resolve("yamato/YamatoNetwork.java"));
        String layout = Files.readString(root.resolve("client/YamatoScreenLayout.java"));
        check(!screen.contains("Button.builder(tr(\"apply_settings\")"), "Manual Apply button still exists");
        check(!layout.contains("int applyY()"), "Sidebar still reserves a row for an obsolete Apply button");
        check(screen.contains("private void settingsChanged(int delayTicks)"), "Setting change no longer initiates autosave");
        check(screen.contains("settingsChanged(0);"), "Dropdown changes do not save immediately");
        check(screen.contains("settingsChanged(5);"), "Seconds edit does not schedule save");
        check(screen.contains("settingsChanged(3);"), "Slider does not schedule save");
        check(count(screen, "tickSettingsAutoSave();") == 2, "Main and compact settings screens must both autosave");
        check(count(screen, "data.put(\"settings\", settingsPayload());") == 2,
                "Both destination modes must send the latest settings atomically with travel");
        check(screen.contains("if (settingsEdited && pendingSettings != null"),
                "Server snapshots can erase in-flight local settings");
        check(screen.contains("public void onClose() {\n        applySettings();"),
                "Closing the main GUI does not flush unsent settings");
        check(screen.contains("parent.applySettings();\n            super.onClose();"),
                "Closing the compact settings screen does not flush unsent settings");
        check(network.contains("if (!applyTravelSettings(player, request))"),
                "Travel handler ignores inline settings");
        check(network.contains("request.contains(\"settings\", Tag.TAG_COMPOUND)"),
                "Travel handler accepts malformed inline settings");
        System.out.println("YamatoSettingsAutoSaveChecks: " + checks + " passed");
    }
}
