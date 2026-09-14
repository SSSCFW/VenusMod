package dev.ssscfw.venusmod.compat.create.client;

import dev.ssscfw.venusmod.compat.create.VenusCreateCompat;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only Create renderer registration, called only when Create + SlashBlade are loaded. */
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
}
