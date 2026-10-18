package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 装着品と設定を単一の機器アイテムへ保存する。動的ARP/フローは保存しない。 */
public final class NetworkDeviceEntity extends BlockEntity implements MenuProvider {
    public static final int PORT_SLOTS=6, COMMON_START=6, COMMON_END=12, WAN_SLOT=12, SLOTS=13;
    private UUID deviceId=UUID.randomUUID();
    private UUID owner;
    private final Set<UUID> trusted=new HashSet<>();
    private NetworkProfile profile;
    private boolean loading;
    private int redstone;
    private ControlSafetySettings.Config appliedSafety=new ControlSafetySettings().config();
    private boolean appliedFailure;
    long nextFailureTime;
    long failureRemaining=1200;
    long scheduleGeneration;
    public final SimpleContainer modules=new SimpleContainer(SLOTS){
        @Override public void setChanged(){super.setChanged();if(!loading){applyModules();NetworkDeviceEntity.this.setChanged();if(level instanceof ServerLevel s)NetworkWorld.get(s).configurationChanged(NetworkDeviceEntity.this);}}
        @Override public int getMaxStackSize(){return 1;}
    };
    public NetworkDeviceEntity(BlockPos pos,BlockState state){super(NetworkContent.DEVICE_ENTITY.get(),pos,state);profile=new NetworkProfile(((NetworkDeviceBlock)state.getBlock()).kind);}
    public String id(){return deviceId.toString();}
    public NetworkProfile profile(){return profile;}
    public void adoptProfile(NetworkProfile value){if(value.kind!=profile.kind)throw new IllegalArgumentException("機種不一致");profile=value;applyModules();reconcileControl(false);}
    public boolean canUse(Player player){return player.isAlive()&&!player.isSpectator()&&(owner==null||owner.equals(player.getUUID())||trusted.contains(player.getUUID())||player.hasPermissions(2));}
    public boolean canControl(Player player){return canUse(player)||profile.publicService&&player.isAlive()&&!player.isSpectator();}
    /** 自動更新用の現在の明示権限。ログアウトで無効化せず、OPの一時権限は記録しない。 */
    public boolean configuredManager(UUID actor){return owner==null||owner.equals(actor)||trusted.contains(actor);}
    public boolean configuredControl(UUID actor){return configuredManager(actor)||profile.publicService;}
    public UUID owner(){return owner;}
    public void setOwnerIfAbsent(Player player){if(owner==null){owner=player.getUUID();setChanged();}}
    public boolean trust(UUID player,boolean add){if(add){if(trusted.size()>=16&&!trusted.contains(player))return false;trusted.add(player);}else trusted.remove(player);setChanged();return true;}
    void renewId(){deviceId=UUID.randomUUID();setChanged();}
    public int redstone(){
        if(profile.failed)return 0;
        var config=profile.controlSafety.config();
        return !config.guarded()||controlActive()?redstone:config.fallback();
    }
    private boolean controlActive(){return level instanceof ServerLevel s&&NetworkWorld.get(s).controls().runtime().active(id(),s.getGameTime());}
    /** 方針変更/ロード/故障で許可を持ち越さない。holdへの切替でも古い出力を復活させない。 */
    void reconcileControl(boolean reloaded){
        if(profile.kind!=DeviceKind.REDSTONE_TERMINAL)return;
        var next=profile.controlSafety.config();boolean changed=!next.equals(appliedSafety);
        if(reloaded||changed||profile.failed&&!appliedFailure){
            int previous=!appliedSafety.guarded()||controlActive()?redstone:appliedSafety.fallback();
            if(level instanceof ServerLevel s)NetworkWorld.get(s).controls().runtime().cancelTarget(id());
            if(next.guarded())setRedstone(next.fallback());
            else if(changed)setRedstone(previous);
        }
        appliedSafety=next;appliedFailure=profile.failed;
    }
    public void setRedstone(int value){redstone=Math.clamp(value,0,15);setChanged();if(level!=null){level.updateNeighborsAt(worldPosition,getBlockState().getBlock());level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}}
    public void applyModules(){
        profile.stability=0;profile.processor=0;profile.buffer=0;
        for(int i=0;i<PORT_SLOTS;i++)profile.ports[i].moduleTier=NetworkContent.portTier(modules.getItem(i));
        for(int i=COMMON_START;i<COMMON_END;i++)switch(NetworkContent.itemId(modules.getItem(i))){
            case "network_stability_50"->profile.stability=Math.max(profile.stability,1);
            case "network_stability_90"->profile.stability=Math.max(profile.stability,2);
            case "network_stability_perfect"->profile.stability=3;
            case "network_processor"->profile.processor+=2;
            case "network_buffer"->profile.buffer++;
        }
        profile.wanTier=NetworkContent.wanTier(modules.getItem(WAN_SLOT));
    }
    public void repair(){profile.failed=false;refreshState();}
    public void refreshState(){
        reconcileControl(false);setChanged();if(level instanceof ServerLevel s){BlockState state=getBlockState();if(state.getValue(NetworkDeviceBlock.FAULTED)!=profile.failed)s.setBlock(worldPosition,state.setValue(NetworkDeviceBlock.FAULTED,profile.failed),3);
            NetworkWorld.get(s).configurationChanged(this);s.updateNeighborsAt(worldPosition,state.getBlock());}
    }
    void failureCheck(ServerLevel server){
        applyModules();double probability=NetworkProfile.failureProbability(NetworkContent.FAILURE_PER_DAY.get(),1200,profile.stability);
        if(!profile.failed&&probability>0&&server.random.nextDouble()<probability){profile.failed=true;refreshState();NetworkWorld.get(server).simulator().log(id(),"内部故障: 修理キットで復旧できます");}
        failureRemaining=1200;setChanged();
    }
    @Override public void onLoad(){super.onLoad();applyModules();if(level instanceof ServerLevel s){NetworkWorld.get(s).add(this);reconcileControl(true);}}
    @Override public void setRemoved(){if(level instanceof ServerLevel s)NetworkWorld.get(s).remove(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){if(level instanceof ServerLevel s)NetworkWorld.get(s).remove(this);super.onChunkUnloaded();}
    @Override public Component getDisplayName(){return Component.translatable(getBlockState().getBlock().getDescriptionId());}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new NetworkMenu(id,inv,this);}
    private CompoundTag persistent(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putUUID("Device",deviceId);if(owner!=null)tag.putUUID("Owner",owner);tag.putString("Config",profile.encode());
        var access=new ListTag();for(UUID id:trusted)access.add(StringTag.valueOf(id.toString()));tag.put("Trusted",access);
        var slots=new ListTag();for(int i=0;i<SLOTS;i++){ItemStack stack=modules.getItem(i);if(!stack.isEmpty()){var entry=new CompoundTag();entry.putInt("Slot",i);entry.put("Stack",stack.save(registries));slots.add(entry);}}
        tag.put("Modules",slots);tag.putInt("Redstone",redstone);
        long remain=level instanceof ServerLevel s&&nextFailureTime>0?Math.max(1,nextFailureTime-s.getGameTime()):failureRemaining;tag.putLong("FailureRemaining",Math.clamp(remain,1,1200));return tag;
    }
    private void restore(CompoundTag tag,HolderLookup.Provider registries){
        loading=true;
        try{
            if(tag.hasUUID("Device"))deviceId=tag.getUUID("Device");if(tag.hasUUID("Owner"))owner=tag.getUUID("Owner");
            if(tag.contains("Config",Tag.TAG_STRING)){try{profile=NetworkProfile.decode(profile.kind,tag.getString("Config"));}catch(IllegalArgumentException invalid){profile.failed=true;}}
            trusted.clear();ListTag ids=tag.getList("Trusted",Tag.TAG_STRING);for(int i=0;i<Math.min(16,ids.size());i++){try{trusted.add(UUID.fromString(ids.getString(i)));}catch(IllegalArgumentException ignored){}}
            modules.clearContent();ListTag slots=tag.getList("Modules",Tag.TAG_COMPOUND);
            for(int i=0;i<Math.min(SLOTS,slots.size());i++){CompoundTag item=slots.getCompound(i);int slot=item.getInt("Slot");if(slot>=0&&slot<SLOTS){ItemStack stack=ItemStack.parseOptional(registries,item.getCompound("Stack"));if(!stack.isEmpty())modules.setItem(slot,stack.copyWithCount(1));}}
            redstone=Math.clamp(tag.getInt("Redstone"),0,15);failureRemaining=tag.contains("FailureRemaining")?Math.clamp(tag.getLong("FailureRemaining"),1,1200):1200;
        }finally{loading=false;applyModules();}
    }
    public ItemStack savedItem(){
        ItemStack stack=new ItemStack(getBlockState().getBlock());if(level!=null){var data=new CompoundTag();data.put("venus_network",persistent(level.registryAccess()));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));}return stack;
    }
    public void restoreItem(ItemStack stack){
        if(level==null)return;CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data!=null){CompoundTag root=data.copyTag();if(root.contains("venus_network",Tag.TAG_COMPOUND))restore(root.getCompound("venus_network"),level.registryAccess());}
        nextFailureTime=0;if(level instanceof ServerLevel s)NetworkWorld.get(s).add(this);reconcileControl(true);refreshState();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.put("Network",persistent(registries));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(tag.contains("Network",Tag.TAG_COMPOUND))restore(tag.getCompound("Network"),registries);}
}
