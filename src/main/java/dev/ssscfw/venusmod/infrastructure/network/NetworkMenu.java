package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** 実スロットはバニラのコンテナ同期を使い、設定コマンドのみ専用Payloadで送る。 */
public final class NetworkMenu extends AbstractContainerMenu {
    private final NetworkDeviceEntity target;
    private final SimpleContainer inventory;
    private final Player player;
    private final BlockPos pos;
    private long lastCommandTick=Long.MIN_VALUE,lastSnapshotTick;
    private int commandsThisTick;
    private String output="help で構成例と操作方法を表示します";
    private String snapshot="接続中…";
    private DeviceKind kind=DeviceKind.PC;
    public NetworkMenu(int id,Inventory inv,RegistryFriendlyByteBuf data){this(id,inv,null,data.readBlockPos());}
    public NetworkMenu(int id,Inventory inv,NetworkDeviceEntity device){this(id,inv,device,device.getBlockPos());}
    private NetworkMenu(int id,Inventory inv,NetworkDeviceEntity device,BlockPos pos){
        super(NetworkContent.MENU.get(),id);target=device;this.pos=pos;player=inv.player;inventory=device==null?new SimpleContainer(NetworkDeviceEntity.SLOTS):device.modules;if(device!=null)kind=device.profile().kind;
        for(int i=0;i<6;i++)addModuleSlot(i,12+i*18,130);
        for(int i=6;i<12;i++)addModuleSlot(i,146+(i-6)*18,130);
        addModuleSlot(12,290,130);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,78+col*18,158+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,78+col*18,216));
    }
    private void addModuleSlot(int index,int x,int y){addSlot(new Slot(inventory,index,x,y){
        @Override public boolean mayPlace(ItemStack item){return index<6?NetworkContent.portTier(item)>0:index==12?NetworkContent.wanTier(item)>0:NetworkContent.commonModule(item);}
        @Override public int getMaxStackSize(){return 1;}
        @Override public boolean mayPickup(Player p){return target==null||target.canUse(p);}
    });}
    @Override public boolean stillValid(Player p){return p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)<=64&&(target==null||!target.isRemoved()&&target.canUse(p)&&p.level().getBlockEntity(pos)==target);}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(stillValid(player))super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();boolean moved=false;
        if(index<NetworkDeviceEntity.SLOTS)moved=moveItemStackTo(stack,NetworkDeviceEntity.SLOTS,slots.size(),true);
        else{for(int i=0;i<NetworkDeviceEntity.SLOTS;i++)if(slots.get(i).mayPlace(stack)&&moveItemStackTo(stack,i,i+1,false)){moved=true;if(stack.isEmpty())break;}}
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
    public String snapshot(){return snapshot;}
    public DeviceKind kind(){return kind;}
    public void receive(int kind,String text){if(kind>=0&&kind<DeviceKind.values().length)this.kind=DeviceKind.values()[kind];snapshot=text;}
    public void command(ServerPlayer sender,String text){
        if(sender!=player||target==null||!stillValid(sender)||text==null||text.length()>512)return;
        long now=sender.serverLevel().getGameTime();if(lastCommandTick==now){if(++commandsThisTick>2)return;}else{if(lastCommandTick!=Long.MIN_VALUE&&now-lastCommandTick<2)return;lastCommandTick=now;commandsThisTick=1;}
        var world=NetworkWorld.get(sender.serverLevel());if(!world.ready()){output=world.diagnostic();sendSnapshot(sender);return;}
        String[] t=text.trim().split("\\s+");var simulator=world.simulator();
        try{
            if(t[0].equals("trust")||t[0].equals("untrust")){
                if(target.owner()!=null&&!target.owner().equals(sender.getUUID())&&!sender.hasPermissions(2)){output="共有権限の変更は所有者のみです";}
                else if(t.length!=2){output="trust プレイヤー名 / untrust プレイヤー名";}
                else{var other=sender.getServer().getPlayerList().getPlayerByName(t[1]);output=other==null?"対象プレイヤーがオンラインではありません":target.trust(other.getUUID(),t[0].equals("trust"))?"共有権限を変更しました":"共有人数の上限です";}
            }else if(t[0].equals("open")||t[0].equals("redstone")){
                if(t.length!=(t[0].equals("open")?2:3))throw new IllegalArgumentException("open IP / redstone IP 0〜15");
                NetworkSimulator.Result route=simulator.ping(target.id(),t[1]);
                if(!route.success())output=route.message();else{var other=world.device(route.destination());
                    if(other==null||!other.canControl(sender))output="通信先のサービス利用権限がありません";
                    else if(t[0].equals("open")){output=other.profile().kind==DeviceKind.SERVER&&other.profile().web?"[ゲーム内管理画面]\n"+other.profile().name+"\n状態: "+(other.profile().failed?"故障":"正常")+"\nIP: "+other.profile().address+"\n上限: "+CableGrade.speed(route.bps())+"\n外部HTML・JavaScriptは使用しません":"仮想Webサービスが停止しています";}
                    else if(other.profile().kind!=DeviceKind.REDSTONE_TERMINAL)output="通信先はレッドストーン端末ではありません";
                    else{int value=Integer.parseInt(t[2]);if(value<0||value>15)throw new IllegalArgumentException("出力は0〜15です");other.setRedstone(value);output="通信に成功し、出力を"+value+"に設定しました";}
                }
            }else{
                NetworkCli.Reply result=NetworkCli.run(simulator,target.id(),text);target.adoptProfile(simulator.nodes().get(target.id()));output=String.join("\n",result.lines());if(result.changed())target.refreshState();
            }
        }catch(IllegalArgumentException exception){output="入力エラー: "+exception.getMessage();}
        sendSnapshot(sender);
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(player instanceof ServerPlayer server&&target!=null&&stillValid(player)){long now=server.serverLevel().getGameTime();if(now-lastSnapshotTick>=20){lastSnapshotTick=now;sendSnapshot(server);}}}
    private void sendSnapshot(ServerPlayer server){
        var world=NetworkWorld.get(server.serverLevel());String text=(target.profile().failed?"[故障] ":"[正常] ")+target.profile().name+" / "+world.diagnostic()+"\n"+output;
        if(text.length()>8000)text=text.substring(0,8000);NetworkPackets.snapshot(server,containerId,target.profile().kind.ordinal(),text);
    }
}
