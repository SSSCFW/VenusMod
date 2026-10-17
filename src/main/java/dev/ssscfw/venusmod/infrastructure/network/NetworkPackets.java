package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 受信時の文字数、メニューID、権限、距離をすべてサーバーで検査する。 */
public final class NetworkPackets {
    private NetworkPackets(){}
    public static void register(IEventBus bus){bus.addListener(NetworkPackets::payloads);}
    private static void payloads(RegisterPayloadHandlersEvent event){var registry=event.registrar("network-1");
        registry.playToServer(Command.TYPE,Command.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof NetworkMenu menu&&menu.containerId==packet.menu)menu.command(player,packet.text);}));
        // クライアント専用クラスを参照せず、共通メニューの表示データだけ更新する。
        registry.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player().containerMenu instanceof NetworkMenu menu&&menu.containerId==packet.menu)menu.receive(packet.kind,packet.text);}));
    }
    public static void snapshot(ServerPlayer player,int menu,int kind,String text){PacketDistributor.sendToPlayer(player,new Snapshot(menu,kind,text));}
    public record Command(int menu,String text)implements CustomPacketPayload{
        public static final Type<Command> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID,"network_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Command> CODEC=new StreamCodec<>(){
            public Command decode(RegistryFriendlyByteBuf buf){return new Command(buf.readVarInt(),buf.readUtf(512));}
            public void encode(RegistryFriendlyByteBuf buf,Command p){buf.writeVarInt(p.menu);buf.writeUtf(p.text,512);}
        };
        @Override public Type<Command> type(){return TYPE;}
    }
    public record Snapshot(int menu,int kind,String text)implements CustomPacketPayload{
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID,"network_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=new StreamCodec<>(){
            public Snapshot decode(RegistryFriendlyByteBuf buf){return new Snapshot(buf.readVarInt(),buf.readVarInt(),buf.readUtf(8000));}
            public void encode(RegistryFriendlyByteBuf buf,Snapshot p){buf.writeVarInt(p.menu);buf.writeVarInt(p.kind);buf.writeUtf(p.text,8000);}
        };
        @Override public Type<Snapshot> type(){return TYPE;}
    }
}
