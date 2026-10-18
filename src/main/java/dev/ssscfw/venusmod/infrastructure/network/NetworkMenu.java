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
    private List<String> formPages=List.of(),formPorts=List.of(),sentFormPages=List.of(),sentFormPorts=List.of();
    private long formRevision;
    private int formResponseId;
    private boolean formAccepted;
    private String formMessage="";
    private static final String FORM_RATE_LIMIT="操作上限です。再試行してください";
    public List<String> formPages(){return formPages;}
    public List<String> formPorts(){return formPorts;}
    public long formRevision(){return formRevision;}
    public int formResponseId(){return formResponseId;}
    public boolean formAccepted(){return formAccepted;}
    public String formMessage(){return formMessage;}
    public void receiveForm(List<String> pages,List<String> ports,int responseId,boolean accepted,String message){
        formPages=List.copyOf(pages);formPorts=List.copyOf(ports);formRevision++;
        formResponseId=responseId;formAccepted=accepted;formMessage=message;
    }
    /** Payload入口と同じ認可条件を実メニューの検査でも使う。実サービスの公開設定は管理許可ではない。 */
    public NetworkForm.Outcome editForm(ServerPlayer sender,String expected,String edit){
        if(sender!=player||target==null||sender.containerMenu!=this||!stillValid(sender))
            return NetworkForm.Outcome.reject("この機器の設定画面を操作する権限がありません");
        if(expected==null||edit==null||expected.length()>NetworkForm.MAX_ENCODED||edit.length()>NetworkForm.MAX_ENCODED)
            return NetworkForm.Outcome.reject("フォーム容量の上限です");
        var world=NetworkWorld.get(sender.serverLevel());
        if(!world.admitRequest(sender.getUUID())){output=FORM_RATE_LIMIT;return NetworkForm.Outcome.reject(FORM_RATE_LIMIT);}
        var sim=world.simulator();var result=NetworkForm.apply(sim,target.id(),expected,edit);
        if(result.changed()){target.adoptProfile(sim.nodes().get(target.id()));target.setChanged();}
        for(String id:sim.drainChangedProfiles()){var changed=world.device(id);if(changed!=null)changed.setChanged();}
        output=result.message();return result;
    }
    public void submitForm(ServerPlayer sender,int requestId,String expected,String edit){
        // 無効な画面や権限失効後には、応答にも最新の機器設定を含めない。
        if(sender!=player||target==null||sender.containerMenu!=this||!stillValid(sender)||requestId<=0)return;
        var result=editForm(sender,expected,edit);
        // レート超過の要求に大きい設定応答を返して増幅させない。通常周期の状態欄で通知する。
        if(!result.message().equals(FORM_RATE_LIMIT))syncForm(sender,requestId,result.accepted(),result.message());
    }
    private void syncForm(ServerPlayer sender,int requestId,boolean accepted,String message){
        if(target==null||sender.containerMenu!=this||!stillValid(sender))return;
        var pages=NetworkForm.captureAll(target.profile());var statuses=new ArrayList<String>(6);
        var sim=NetworkWorld.get(sender.serverLevel()).simulator();
        for(int i=0;i<6;i++){String status=sim.portStatus(target.id(),i);statuses.add(status.length()>160?status.substring(0,160):status);}
        if(requestId==0&&pages.equals(sentFormPages)&&statuses.equals(sentFormPorts))return;
        sentFormPages=pages;sentFormPorts=List.copyOf(statuses);
        NetworkPackets.formState(sender,containerId,requestId,accepted,message,pages,statuses);
    }
    public String snapshot(){return snapshot;}
    public DeviceKind kind(){return kind;}
    public void receive(int kind,String text){if(kind>=0&&kind<DeviceKind.values().length)this.kind=DeviceKind.values()[kind];snapshot=text;}
    public void command(ServerPlayer sender,String text){
        if(sender!=player||target==null||!stillValid(sender)||text==null||text.length()>512||text.codePoints().anyMatch(Character::isISOControl))return;
        long now=sender.serverLevel().getGameTime();if(lastCommandTick==now){if(++commandsThisTick>2)return;}else{if(lastCommandTick!=Long.MIN_VALUE&&now-lastCommandTick<2)return;lastCommandTick=now;commandsThisTick=1;}
        var world=NetworkWorld.get(sender.serverLevel());
        // メニューを開き直しても、ディメンション/プレイヤー共通の予算を使う。
        if(!world.admitRequest(sender.getUUID())){output="同一tickの通信操作上限です。再試行してください";return;}
        if(!world.simulator().nodes().containsKey(target.id())){output="機器数上限または未登録の機器です";sendSnapshot(sender);return;}
        String[] t=text.trim().split("\\s+");var simulator=world.simulator();
        try{
            var service=NetworkServiceCommands.run(simulator,target.id(),text);
            if(service.handled()){
                target.adoptProfile(simulator.nodes().get(target.id()));output=String.join("\n",service.lines());if(service.changed()){if(DnsCommands.handles(t)||SyslogCommands.handles(t)||MonitorCommands.handles(t))target.setChanged();else target.refreshState();}
            }else if(t[0].equals("topology")){
                if(t.length==1)output=world.diagnostic()+"\n今回tickの局所解析作業: "+world.topologyLastSteps()+" / 累計: "+world.topologySteps();
                else if(t.length==2&&t[1].equals("rebuild")){
                    if(!sender.hasPermissions(2))output="全体再解析は管理者専用です (通常の配線変更は自動で局所解析)";
                    else{world.rebuildTopology();output="全配線の明示的な再解析を開始しました。影響する通信は再確立します。";}
                }else output="topology / topology rebuild (管理者専用)";
            }else if(t[0].equals("trust")||t[0].equals("untrust")){
                if(target.owner()!=null&&!target.owner().equals(sender.getUUID())&&!sender.hasPermissions(2)){output="共有権限の変更は所有者のみです";}
                else if(t.length!=2){output="trust プレイヤー名 / untrust プレイヤー名";}
                else{var other=sender.getServer().getPlayerList().getPlayerByName(t[1]);output=other==null?"対象プレイヤーがオンラインではありません":target.trust(other.getUUID(),t[0].equals("trust"))?"共有権限を変更しました":"共有人数の上限です";}
            }else if(t[0].equals("open")||t[0].equals("redstone")){
                if(t.length!=(t[0].equals("open")?2:3))throw new IllegalArgumentException("open 名前/IP[:PORT] / redstone 名前/IP[:PORT] 0〜15");
                int expectedPort=t[0].equals("open")?NetworkSimulator.WEB_PORT:NetworkSimulator.CONTROL_PORT;
                ServiceAddress endpoint=simulator.resolveEndpoint(target.id(),t[1],expectedPort);
                NetworkSimulator.Result route=simulator.requestService(target.id(),endpoint.address(),"tcp",endpoint.port(),expectedPort);
                if(!route.success())output=route.message();else{var other=world.device(route.destination());
                    if(other==null||!other.canControl(sender))output="通信先のサービス利用権限がありません";
                    else if(t[0].equals("open")){output=other.profile().kind==DeviceKind.SERVER&&other.profile().web?"[ゲーム内管理画面]\n"+other.profile().name+"\n状態: "+(other.profile().failed?"故障":"正常")+"\nIP: "+other.profile().address+"\n上限: "+CableGrade.speed(route.bps())+"\n外部HTML・JavaScriptは使用しません":"仮想Webサービスが停止しています";}
                    else if(other.profile().kind!=DeviceKind.REDSTONE_TERMINAL)output="通信先はレッドストーン端末ではありません";
                    else{int value=Integer.parseInt(t[2]);if(value<0||value>15)throw new IllegalArgumentException("出力は0〜15です");other.setRedstone(value);output="通信に成功し、出力を"+value+"に設定しました";}
                }
            }else{
                NetworkCli.Reply result=NetworkCli.run(simulator,target.id(),text);target.adoptProfile(simulator.nodes().get(target.id()));output=String.join("\n",result.lines());if(result.changed())target.refreshState();
                if(t[0].equals("help"))output="図付きガイドをボタンから開けます。\n追加: acl / dhcp-relay / dhcp-pool / dhcp-bindings\n"+output;
            }
        }catch(IllegalArgumentException exception){output="入力エラー: "+exception.getMessage();}
        // クライアントだけでなくDHCPサーバーの配布予約も保存する。
        for(String id:simulator.drainChangedProfiles()){var changed=world.device(id);if(changed!=null)changed.setChanged();}
        sendSnapshot(sender);
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(player instanceof ServerPlayer server&&target!=null&&stillValid(player)){long now=server.serverLevel().getGameTime();if(now-lastSnapshotTick>=20){lastSnapshotTick=now;sendSnapshot(server);}}}
    private void sendSnapshot(ServerPlayer server){
        var world=NetworkWorld.get(server.serverLevel());String text=(target.profile().failed?"[故障] ":"[正常] ")+target.profile().name+" / "+world.diagnostic()+"\n"+output;
        if(text.length()>8000)text=text.substring(0,8000);NetworkPackets.snapshot(server,containerId,target.profile().kind.ordinal(),text);syncForm(server,0,true,"");
    }
}
