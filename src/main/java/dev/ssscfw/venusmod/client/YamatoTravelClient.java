package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.YamatoCompat;
import dev.ssscfw.venusmod.yamato.YamatoNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

/** クライアント専用のHキー登録。GUIはサーバーが認可したスナップショットから開く。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YamatoTravelClient {
    private static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.venusmod.yamato_travel", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H, "key.categories.venusmod");
    private static int awaitingOpenTicks;
    private static int refreshTicks;

    private YamatoTravelClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    public static Component openKeyLabel() {
        return OPEN_KEY.getTranslatedKeyMessage();
    }

    /** ネットワークハンドラはクライアントスレッドへenqueueしてから呼び出す。 */
    public static void receive(CompoundTag data, boolean open) {
        Minecraft client = Minecraft.getInstance();
        if (open) {
            boolean requested = awaitingOpenTicks > 0;
            awaitingOpenTicks = 0;
            if (requested && client.screen == null && client.player != null
                    && !YamatoCompat.heldYamato(client.player).isEmpty()) {
                client.setScreen(new YamatoTravelScreen(data));
            }
        } else if (client.screen instanceof YamatoTravelScreen screen) {
            screen.receiveSnapshot(data);
        } else if (client.screen instanceof YamatoTravelScreen.Child screen) {
            screen.parent().receiveSnapshot(data);
        }
        // 遅れて届いた通常更新から、閉じた画面を再度開かない。
    }

    @EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        private Input() {}

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            if (YamatoCompat.isYamato(event.getItemStack())) {
                event.getToolTip().add(Component.translatable("tooltip.venusmod.yamato_travel", openKeyLabel())
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && (client.screen instanceof YamatoTravelScreen
                    || client.screen instanceof YamatoTravelScreen.Child)) {
                if (++refreshTicks >= 40) {
                    refreshTicks = 0;
                    YamatoNetwork.sendAction("refresh", new CompoundTag());
                }
            } else refreshTicks = 0;
            boolean pressed = false;
            while (OPEN_KEY.consumeClick()) {
                // GUI表示中や未所持時も消費し、後で持った瞬間に過去の入力を実行しない。
                pressed = true;
            }
            if (awaitingOpenTicks > 0) awaitingOpenTicks--;
            if (client.player == null || client.screen != null
                    || YamatoCompat.heldYamato(client.player).isEmpty()) {
                awaitingOpenTicks = 0;
                return;
            }
            if (pressed && awaitingOpenTicks == 0) {
                awaitingOpenTicks = 100;
                YamatoNetwork.sendAction("open", new CompoundTag());
            }
        }
    }
}
