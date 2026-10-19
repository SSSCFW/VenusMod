package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.YamatoCompat;
import dev.ssscfw.venusmod.yamato.YamatoNetwork;
import dev.ssscfw.venusmod.yamato.YamatoVoidSlashCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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
        private static final YamatoVoidSlashCommand VOID_SLASH = new YamatoVoidSlashCommand();

        private Input() {}

        /** GUI操作や他の抜刀剣では受付状態を残さない。技はメインハンドの閻魔刀限定。 */
        private static boolean canUseVoidSlash(Minecraft client) {
            return client.player != null && client.level != null && client.screen == null
                    && !client.player.isSpectator()
                    && YamatoCompat.isYamato(client.player.getMainHandItem());
        }

        /** 移動キーのバインド設定を尊重し、押しっぱなしのリピートを段階入力として扱わない。 */
        @SubscribeEvent
        public static void commandKey(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS) return;
            Minecraft client = Minecraft.getInstance();
            if (!canUseVoidSlash(client)) {
                VOID_SLASH.reset();
                return;
            }
            long tick = client.level.getGameTime();
            if (client.options.keyDown.matches(event.getKey(), event.getScanCode())) {
                // Wを先に押している状態からSを追加してもS→Wとはみなさない。
                if (client.options.keyUp.isDown()) {
                    VOID_SLASH.reset();
                    return;
                }
                VOID_SLASH.pressBack(tick);
                YamatoNetwork.sendAction("void_slash_back", new CompoundTag());
            } else if (client.options.keyUp.matches(event.getKey(), event.getScanCode())) {
                if (VOID_SLASH.pressForward(tick)) {
                    YamatoNetwork.sendAction("void_slash_forward", new CompoundTag());
                }
            }
        }

        /**
         * S→Wの直後の使用入力だけを既存の時空裂閃へ置換する。
         * W押下イベントが同一フレームでまだ発火していない場合も、現在のW状態を確認する。
         */
        @SubscribeEvent
        public static void useAfterCommand(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
            Minecraft client = Minecraft.getInstance();
            if (!canUseVoidSlash(client)) {
                VOID_SLASH.reset();
                return;
            }
            long tick = client.level.getGameTime();
            if (VOID_SLASH.awaitingForward(tick) && client.options.keyUp.isDown()
                    && VOID_SLASH.pressForward(tick)) {
                YamatoNetwork.sendAction("void_slash_forward", new CompoundTag());
            }
            if (VOID_SLASH.use(tick)) {
                YamatoNetwork.sendAction("void_slash", new CompoundTag());
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            VOID_SLASH.reset();
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            if (YamatoCompat.isYamato(event.getItemStack())) {
                event.getToolTip().add(Component.translatable("tooltip.venusmod.yamato_travel", openKeyLabel())
                        .withStyle(ChatFormatting.AQUA));
                event.getToolTip().add(Component.translatable("tooltip.venusmod.yamato_void_slash")
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (!canUseVoidSlash(client)) VOID_SLASH.reset();
            else VOID_SLASH.expire(client.level.getGameTime());
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
