package dev.ssscfw.venusmod.compat.create.client;

import dev.ssscfw.venusmod.compat.create.VenusCreateCompat;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Create導入時だけ呼び出すクライアント側の描画・GUI登録。 */
public final class VenusCreateClientCompat {
    private VenusCreateClientCompat() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                VenusCreateCompat.BLADE_REPAIR_STATION_BE.get(),
                BladeMachineRenderer::new);
        event.registerBlockEntityRenderer(
                VenusCreateCompat.BLADE_BREAKER_BE.get(),
                BladeMachineRenderer::new);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(
                VenusCreateCompat.VENUS_BLADE_FORGE_MENU.get(),
                VenusBladeForgeScreen::new);
    }
}
