package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import dev.ssscfw.venusmod.treasure.TreasureRules;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 専用サーバーからはロードしない、描画と攻撃/使用/Fキーの入口。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KingsTreasureClient {
    private KingsTreasureClient() {}
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(KingsTreasure.BLADE.get(), RoyalBladeRenderer::new);
    }

    @EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        private static final TreasureRules.PressLatch LATCH = new TreasureRules.PressLatch();
        private Input() {}

        /**
         * バニラの持ち替え処理より前にswap-offhand(F既定)のclickを消費する。
         * 王の財宝を持っている間だけFを最大射出本数の切替へ専有する。
         */
        @SubscribeEvent public static void preTick(ClientTickEvent.Pre event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null || !KingsTreasure.isHeld(client.player)) return;
            if (!client.options.keySwapOffhand.consumeClick()) return;
            while (client.options.keySwapOffhand.consumeClick()) {
                // 同tickに溜まったリピート入力を1回にまとめる。
            }
            PacketDistributor.sendToServer(new KingsTreasure.LimitAction(true));
        }

        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null || !KingsTreasure.isHeld(client.player)) {
                LATCH.release(false, false);
            } else {
                LATCH.release(client.options.keyAttack.isDown(), client.options.keyUse.isDown());
            }
        }

        @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null || !KingsTreasure.isHeld(client.player)) return;
            if (!event.isAttack() && !event.isUseItem()) return;
            event.setCanceled(true);
            event.setSwingHand(false);
            if (LATCH.press(event.isAttack())) PacketDistributor.sendToServer(new KingsTreasure.Action(event.isAttack()));
        }
    }
}
