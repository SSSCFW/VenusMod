package dev.ssscfw.venusmod.storagebox;

import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Storage Boxのキー操作をサーバーへ送る最小C2Sプロトコル。 */
public final class StorageBoxNetwork {
    public enum Action {
        INSERT_ALL,
        EXTRACT_STACK,
        DROP_STACK,
        TOGGLE_AUTO_COLLECT;

        static Action fromId(int id) {
            return id >= 0 && id < values().length ? values()[id] : INSERT_ALL;
        }
    }

    private StorageBoxNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(StorageBoxNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ActionPayload.TYPE, ActionPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) handle(player, Action.fromId(payload.action()));
                }));
    }

    private static void handle(ServerPlayer player, Action action) {
        if (!player.isAlive() || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof StorageBoxItem)) return;
        var box = player.getMainHandItem();
        switch (action) {
            case INSERT_ALL -> StorageBoxActions.insertAll(player, box);
            case EXTRACT_STACK -> StorageBoxActions.extractToInventory(player, box);
            case DROP_STACK -> StorageBoxActions.dropStack(player, box);
            case TOGGLE_AUTO_COLLECT -> {
                boolean enabled = StorageBoxActions.toggleAutoCollect(player, box);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        enabled ? "message.venusmod.storage_box.auto_on" : "message.venusmod.storage_box.auto_off"), true);
            }
        }
    }

    public record ActionPayload(int action) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "storage_box_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ActionPayload::action, ActionPayload::new);
        @Override public Type<ActionPayload> type() { return TYPE; }
    }
}
