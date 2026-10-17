package dev.ssscfw.venusmod.yamato;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.YamatoCompat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 座標をクライアントから受け取らず、所有する保存地点IDだけを操作する。 */
public final class YamatoNetwork {
    private static Consumer<StatePayload> clientState = ignored -> {};
    private static Consumer<CutPayload> clientCut = ignored -> {};
    private YamatoNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(YamatoNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(YamatoRifts::onTick);
        NeoForge.EVENT_BUS.addListener(YamatoNetwork::login);
        NeoForge.EVENT_BUS.addListener(YamatoNetwork::changedDimension);
    }

    /** クライアント側の初期化からのみ呼ぶ。共通クラスはclientクラスをリンクしない。 */
    public static void clientHandlers(Consumer<StatePayload> state, Consumer<CutPayload> cut) {
        clientState = state;
        clientCut = cut;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) handle(player, payload);
                }));
        registrar.playToClient(StatePayload.TYPE, StatePayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> clientState.accept(payload)));
        registrar.playToClient(CutPayload.TYPE, CutPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> clientCut.accept(payload)));
    }

    public static void sendAction(String action, CompoundTag request) {
        PacketDistributor.sendToServer(new ActionPayload(action, request));
    }

    private static void handle(ServerPlayer player, ActionPayload payload) {
        var rifts = YamatoRifts.get(player.server);
        if (!rifts.allowRequest(player.getUUID(), player.server.overworld().getGameTime())) return;
        String action = payload.action();
        CompoundTag request = payload.request();
        if (request.getAllKeys().size() > 8) return;
        // 所有者による閉鎖は、持ち替え直後に届いた要求でも実行できる。
        if (action.equals("close") || action.equals("close_all")) {
            if (action.equals("close_all")) rifts.closeAll(player.getUUID(), player.server);
            else rifts.close(player.getUUID(), readId(request), player.server);
            sendBook(player, false);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || YamatoCompat.heldYamato(player).isEmpty()) return;
        switch (action) {
            case "open", "refresh" -> {
                rifts.watch(player);
                sendBook(player, action.equals("open"));
            }
            case "travel" -> rifts.requestTravel(player, readId(request));
            case "save", "folder", "rename", "move", "reorder", "delete", "settings", "sort" -> {
                if (!YamatoTravelData.get(player.server).apply(player, action, request))
                    message(player, "invalid_request");
                sendBook(player, false);
            }
            default -> { }
        }
    }

    public static java.util.UUID readId(CompoundTag tag) {
        if (tag.hasUUID("id")) return tag.getUUID("id");
        return YamatoTravelRules.parseId(tag.getString("id"));
    }

    public static void sendBook(ServerPlayer player, boolean open) {
        CompoundTag data = YamatoTravelData.get(player.server).snapshot(player.getUUID());
        data.put("active", YamatoRifts.get(player.server).active(player.getUUID(), player.server));
        PacketDistributor.sendToPlayer(player, new StatePayload(0, data, open));
    }

    public static void sendWorld(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new StatePayload(1, YamatoRifts.get(player.server).worldView(player), false));
    }

    public static void sendCut(ServerPlayer player, InteractionHand hand, int stage) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new CutPayload(player.getId(), hand.ordinal(), stage));
    }

    public static void message(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable("message.venusmod.yamato." + suffix), true);
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendWorld(player);
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendWorld(player);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, path));
    }

    public record ActionPayload(String action, CompoundTag request) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = YamatoNetwork.type("yamato_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = new StreamCodec<>() {
            @Override public ActionPayload decode(RegistryFriendlyByteBuf buf) {
                String action = buf.readUtf(24);
                net.minecraft.nbt.Tag decoded = FriendlyByteBuf.readNbt(buf, new NbtAccounter(16384, 12));
                if (decoded == null) return new ActionPayload(action, new CompoundTag());
                if (!(decoded instanceof CompoundTag request))
                    throw new io.netty.handler.codec.DecoderException("Yamato action requires compound NBT");
                return new ActionPayload(action, request);
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, ActionPayload value) {
                buf.writeUtf(value.action(), 24);
                buf.writeNbt(value.request());
            }
        };
        @Override public Type<ActionPayload> type() { return TYPE; }
    }

    /** 多数の地点・日本語名でもS2Cの1MiB制限を超えないよう表示状態を圧縮する。 */
    public record StatePayload(int kind, CompoundTag data, boolean open) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = YamatoNetwork.type("yamato_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = new StreamCodec<>() {
            @Override public StatePayload decode(RegistryFriendlyByteBuf buf) {
                int kind = buf.readVarInt();
                boolean open = buf.readBoolean();
                try {
                    return new StatePayload(kind, NbtIo.readCompressed(new ByteArrayInputStream(
                            buf.readByteArray(900 * 1024)), new NbtAccounter(32 * 1024 * 1024L, 16)), open);
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, StatePayload value) {
                buf.writeVarInt(value.kind());
                buf.writeBoolean(value.open());
                try {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    NbtIo.writeCompressed(value.data(), stream);
                    byte[] bytes = stream.toByteArray();
                    if (bytes.length > 900 * 1024) throw new IllegalArgumentException("Yamato snapshot too large");
                    buf.writeByteArray(bytes);
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
        };
        @Override public Type<StatePayload> type() { return TYPE; }
    }

    public record CutPayload(int entityId, int hand, int stage) implements CustomPacketPayload {
        public static final Type<CutPayload> TYPE = YamatoNetwork.type("yamato_cut");
        public static final StreamCodec<RegistryFriendlyByteBuf, CutPayload> CODEC = new StreamCodec<>() {
            @Override public CutPayload decode(RegistryFriendlyByteBuf buf) {
                return new CutPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, CutPayload value) {
                buf.writeVarInt(value.entityId()); buf.writeVarInt(value.hand()); buf.writeVarInt(value.stage());
            }
        };
        @Override public Type<CutPayload> type() { return TYPE; }
    }
}
