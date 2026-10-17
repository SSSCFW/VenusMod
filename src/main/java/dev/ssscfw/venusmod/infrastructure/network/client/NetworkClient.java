package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 専用サーバーから画面クラスをロードしない。 */
@EventBusSubscriber(modid=VenusMod.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class NetworkClient {
    private NetworkClient() {}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(NetworkContent.MENU.get(), NetworkScreen::new);
    }
}
