package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkForm;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkGogglesReadout;
import dev.ssscfw.venusmod.infrastructure.network.core.DeviceKind;
import net.minecraft.core.BlockPos;
import java.util.*;
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
    private static void payloads(RegisterPayloadHandlersEvent event){var registry=event.registrar("network-3");
        registry.playToServer(Command.TYPE,Command.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof NetworkMenu menu&&menu.containerId==packet.menu)menu.command(player,packet.text);}));
        registry.playToServer(FormEdit.TYPE,FormEdit.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof NetworkMenu menu&&menu.containerId==packet.menu)
                menu.submitForm(player,packet.requestId,packet.expected,packet.edit);
        }));
        registry.playToClient(FormState.TYPE,FormState.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player().containerMenu instanceof NetworkMenu menu&&menu.containerId==packet.menu)
                menu.receiveForm(packet.pages,packet.ports,packet.requestId,packet.accepted,packet.message);
        }));
        // クライアント専用のHUDクラスをサーバーから読み込まない。
        registry.playToClient(GoggleView.TYPE,GoggleView.CODEC,(packet,context)->
                context.enqueueWork(()->NetworkGogglesStatus.accept(packet)));
        // クライアント専用クラスを参照せず、共通メニューの表示データだけ更新する。
        registry.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player().containerMenu instanceof NetworkMenu menu&&menu.containerId==packet.menu)menu.receive(packet.kind,packet.text);}));
    }
    public static void snapshot(ServerPlayer player,int menu,int kind,String text){PacketDistributor.sendToPlayer(player,new Snapshot(menu,kind,text));}
    public static void formState(ServerPlayer player,int menu,int requestId,boolean accepted,String message,List<String> pages,List<String> ports){
        PacketDistributor.sendToPlayer(player,new FormState(menu,requestId,accepted,message,pages,ports));
    }
    /** ゴーグルの閲覧情報。所有者検査はサーバー側で済ませ、全6ポートを固定長で送る。 */
    public record GoggleView(String dimension,BlockPos pos,int kind,String name,
                             boolean faulted,boolean permitted,List<NetworkGogglesReadout.Port> ports)
                             implements CustomPacketPayload {
        public GoggleView {
            ports=List.copyOf(ports);
            if(ports.size()!=6||kind<0||kind>=DeviceKind.values().length
                    ||dimension.length()>128||name.length()>48)
                throw new IllegalArgumentException("不正なゴーグルデータ");
        }
        public static final Type<GoggleView> TYPE=new Type<>(
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID,"network_goggles_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,GoggleView> CODEC=new StreamCodec<>(){
            @Override public GoggleView decode(RegistryFriendlyByteBuf b){
                String dimension=b.readUtf(128);
                BlockPos pos=b.readBlockPos();
                int kind=b.readVarInt();
                String name=b.readUtf(48);
                boolean faulted=b.readBoolean(),permitted=b.readBoolean();
                List<NetworkGogglesReadout.Port> ports=new ArrayList<>(6);
                for(int i=0;i<6;i++){
                    boolean visible=b.readBoolean(),connected=b.readBoolean();
                    boolean enabled=b.readBoolean(),linkUp=b.readBoolean();
                    String cidr=b.readUtf(24);
                    long maximum=b.readVarLong();
                    int vlan=b.readVarInt();
                    boolean trunk=b.readBoolean();
                    ports.add(new NetworkGogglesReadout.Port(
                            visible,connected,enabled,linkUp,cidr,maximum,vlan,trunk));
                }
                return new GoggleView(dimension,pos,kind,name,faulted,permitted,ports);
            }
            @Override public void encode(RegistryFriendlyByteBuf b,GoggleView p){
                b.writeUtf(p.dimension,128);b.writeBlockPos(p.pos);
                b.writeVarInt(p.kind);b.writeUtf(p.name,48);
                b.writeBoolean(p.faulted);b.writeBoolean(p.permitted);
                for(var port:p.ports){
                    b.writeBoolean(port.visible());b.writeBoolean(port.connected());
                    b.writeBoolean(port.enabled());b.writeBoolean(port.linkUp());
                    b.writeUtf(port.cidr(),24);
                    b.writeVarLong(port.maximumBps());b.writeVarInt(port.vlan());
                    b.writeBoolean(port.trunk());
                }
            }
        };
        @Override public Type<GoggleView> type(){return TYPE;}
    }
    public record FormEdit(int menu,int requestId,String expected,String edit) implements CustomPacketPayload {
        public static final Type<FormEdit> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID,"network_form_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,FormEdit> CODEC=new StreamCodec<>(){
            public FormEdit decode(RegistryFriendlyByteBuf b){return new FormEdit(b.readVarInt(),b.readVarInt(),b.readUtf(NetworkForm.MAX_ENCODED),b.readUtf(NetworkForm.MAX_ENCODED));}
            public void encode(RegistryFriendlyByteBuf b,FormEdit p){b.writeVarInt(p.menu);b.writeVarInt(p.requestId);b.writeUtf(p.expected,NetworkForm.MAX_ENCODED);b.writeUtf(p.edit,NetworkForm.MAX_ENCODED);}
        };
        @Override public Type<FormEdit> type(){return TYPE;}
    }
    /** 個数は固定で、クライアントから配列長を指定させない。非公開の設定全文は含まない。 */
    public record FormState(int menu,int requestId,boolean accepted,String message,List<String> pages,List<String> ports) implements CustomPacketPayload {
        public FormState {pages=List.copyOf(pages);ports=List.copyOf(ports);if(pages.size()!=NetworkForm.PAGE_COUNT||ports.size()!=6)throw new IllegalArgumentException("フォームページ数が不正です");}
        public static final Type<FormState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID,"network_form_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,FormState> CODEC=new StreamCodec<>(){
            public FormState decode(RegistryFriendlyByteBuf b){
                int menu=b.readVarInt(),request=b.readVarInt();boolean accepted=b.readBoolean();String message=b.readUtf(256);
                var pages=new ArrayList<String>(NetworkForm.PAGE_COUNT);for(int i=0;i<NetworkForm.PAGE_COUNT;i++)pages.add(b.readUtf(NetworkForm.MAX_ENCODED));
                var ports=new ArrayList<String>(6);for(int i=0;i<6;i++)ports.add(b.readUtf(160));return new FormState(menu,request,accepted,message,pages,ports);
            }
            public void encode(RegistryFriendlyByteBuf b,FormState p){b.writeVarInt(p.menu);b.writeVarInt(p.requestId);b.writeBoolean(p.accepted);b.writeUtf(p.message,256);
                for(String page:p.pages)b.writeUtf(page,NetworkForm.MAX_ENCODED);for(String port:p.ports)b.writeUtf(port,160);}
        };
        @Override public Type<FormState> type(){return TYPE;}
    }
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
