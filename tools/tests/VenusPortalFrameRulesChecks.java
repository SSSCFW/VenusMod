import dev.ssscfw.venusmod.world.VenusPortalFrameRules;

/** 金ブロック・ダミー・混在の枠互換を純Javaで検査する。 */
public final class VenusPortalFrameRulesChecks {
    private static int checks;
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(VenusPortalFrameRules.accepts(true, false), "金ブロック単独");
        check(VenusPortalFrameRules.accepts(false, true), "金ブロックダミー単独");
        check(VenusPortalFrameRules.accepts(true, true), "金/ダミー混在");
        check(!VenusPortalFrameRules.accepts(false, false), "無関係ブロックは拒否");
        System.out.println("VenusPortalFrameRulesChecks: " + checks + " passed");
    }
}
