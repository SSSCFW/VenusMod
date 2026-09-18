package dev.ssscfw.venusmod.world;

/** 金星ポータルの枠素材互換をMinecraft非依存で固定する。 */
public final class VenusPortalFrameRules {
    private VenusPortalFrameRules() {}

    public static boolean accepts(boolean vanillaGold, boolean dummyGold) {
        return vanillaGold || dummyGold;
    }
}
