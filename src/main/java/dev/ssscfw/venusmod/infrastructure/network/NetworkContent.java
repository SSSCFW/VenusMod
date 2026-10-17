package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import dev.ssscfw.venusmod.registry.ModCreativeTabs;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Createなしで登録・動作するネットワーク機器。 */
public final class NetworkContent {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(VenusMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(VenusMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,VenusMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,VenusMod.MOD_ID);
    public static final Map<CableGrade,DeferredBlock<NetworkCableBlock>> CABLES=new EnumMap<>(CableGrade.class);
    public static final Map<DeviceKind,DeferredBlock<NetworkDeviceBlock>> DEVICES=new EnumMap<>(DeviceKind.class);
    public static final Map<String,DeferredItem<? extends Item>> NETWORK_ITEMS=new LinkedHashMap<>();
    public static final String[] MODULE_IDS={"network_gold_circuit","network_gold_bundle","network_port_10g","network_port_40g","network_port_100g","network_port_400g","network_stability_50","network_stability_90","network_stability_perfect","network_processor","network_buffer","network_wan_1","network_wan_2","network_wan_3","network_wan_4","network_wan_5","network_repair_kit","network_configurator"};
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NetworkDeviceEntity>> DEVICE_ENTITY;
    public static final DeferredHolder<MenuType<?>,MenuType<NetworkMenu>> MENU;
    public static final ModConfigSpec CONFIG;
    public static final ModConfigSpec.DoubleValue FAILURE_PER_DAY;
    public static final ModConfigSpec.IntValue TOPOLOGY_BUDGET;
    static {
        for(CableGrade grade:CableGrade.values()){
            var block=BLOCKS.register(grade.id(),()->new NetworkCableBlock(grade,BlockBehaviour.Properties.of().strength(0.5F).noOcclusion().sound(SoundType.METAL)));
            CABLES.put(grade,block);NETWORK_ITEMS.put(grade.id(),ITEMS.registerSimpleBlockItem(block));
        }
        for(DeviceKind kind:DeviceKind.values()){
            var block=BLOCKS.register(kind.id(),()->new NetworkDeviceBlock(kind,BlockBehaviour.Properties.of().strength(2.5F,6.0F).noOcclusion().sound(SoundType.METAL)));
            DEVICES.put(kind,block);NETWORK_ITEMS.put(kind.id(),ITEMS.registerSimpleBlockItem(block,new Item.Properties().stacksTo(1)));
        }
        for(String id:MODULE_IDS)NETWORK_ITEMS.put(id,ITEMS.register(id,()->new NetworkToolItem(id,new Item.Properties().stacksTo(id.equals("network_configurator")?1:64))));
        DEVICE_ENTITY=ENTITIES.register("network_device",()->BlockEntityType.Builder.of(NetworkDeviceEntity::new,DEVICES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
        MENU=MENUS.register("network_device",()->IMenuTypeExtension.create(NetworkMenu::new));
        var config=new ModConfigSpec.Builder();config.push("network");
        FAILURE_PER_DAY=config.comment("内部ランダム故障の確率/稼働24000tick。0で無効。完全安定化は常に0。").defineInRange("failureProbabilityPerDay",0.0005,0.0,0.1);
        TOPOLOGY_BUDGET=config.comment("1ディメンション1tickの配線探索上限。チャンクを強制ロードしません。").defineInRange("topologyWorkPerTick",512,32,4096);
        config.pop();CONFIG=config.build();
    }
    private NetworkContent(){}
    public static void register(IEventBus bus,ModContainer container){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);container.registerConfig(ModConfig.Type.SERVER,CONFIG,"venusmod-network-server.toml");
        bus.addListener(NetworkContent::creative);NetworkPackets.register(bus);
        NeoForge.EVENT_BUS.addListener(NetworkWorld::tick);NeoForge.EVENT_BUS.addListener(NetworkWorld::stopped);
        NeoForge.EVENT_BUS.addListener(NetworkWorld::breaking);NeoForge.EVENT_BUS.addListener(NetworkWorld::chunkLoaded);NeoForge.EVENT_BUS.addListener(NetworkWorld::chunkUnloaded);
    }
    private static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(ModCreativeTabs.VENUS_TAB_KEY))for(var item:NETWORK_ITEMS.values())e.accept(item.get());}
    public static int portTier(ItemStack item){String id=itemId(item);return switch(id){case "network_port_10g"->1;case "network_port_40g"->2;case "network_port_100g"->3;case "network_port_400g"->4;default->0;};}
    public static int wanTier(ItemStack item){String id=itemId(item);for(int i=1;i<=5;i++)if(id.equals("network_wan_"+i))return i;return 0;}
    public static String itemId(ItemStack stack){if(stack.isEmpty())return "";var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());return id.getNamespace().equals(VenusMod.MOD_ID)?id.getPath():"";}
    public static boolean commonModule(ItemStack item){String id=itemId(item);return id.equals("network_processor")||id.equals("network_buffer")||id.startsWith("network_stability_");}
}
