package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.registry.ModMenus;
import dev.ssscfw.venusmod.storagebox.StorageBoxNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Storage Boxの画面、キー入力、インベントリアイコン装飾をクライアント側だけで登録する。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StorageBoxClient {
    private static final KeyMapping ACTION_KEY = new KeyMapping(
            "key.venusmod.storage_box", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_APOSTROPHE, "key.categories.venusmod");

    private StorageBoxClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ACTION_KEY);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STORAGE_BOX.get(), StorageBoxScreen::new);
    }

    @SubscribeEvent
    public static void registerDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.STORAGE_BOX.get(), new StorageBoxItemDecorator());
    }

    @EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        private Input() {}

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null
                    || !client.player.getMainHandItem().is(ModItems.STORAGE_BOX.get())) return;
            if (!ACTION_KEY.consumeClick()) return;
            while (ACTION_KEY.consumeClick()) {
                // 同tickのキーリピートを1回へまとめる。
            }
            boolean shift = Screen.hasShiftDown();
            boolean control = Screen.hasControlDown();
            StorageBoxNetwork.Action action = shift && control
                    ? StorageBoxNetwork.Action.DROP_STACK
                    : shift ? StorageBoxNetwork.Action.EXTRACT_STACK
                    : control ? StorageBoxNetwork.Action.TOGGLE_AUTO_COLLECT
                    : StorageBoxNetwork.Action.INSERT_ALL;
            PacketDistributor.sendToServer(new StorageBoxNetwork.ActionPayload(action.ordinal()));
        }
    }
}
