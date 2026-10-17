package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.CableGrade;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** 修理・診断とモジュール説明。無制限の任意アイテム操作はしない。 */
public final class NetworkToolItem extends Item {
    private final String id;
    public NetworkToolItem(String id,Properties properties){super(properties);this.id=id;}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();
        if(id.equals("network_repair_kit")&&level.getBlockEntity(pos) instanceof NetworkDeviceEntity device){
            if(level.isClientSide)return InteractionResult.SUCCESS;
            if(!(context.getPlayer() instanceof ServerPlayer player)||!device.canUse(player))return InteractionResult.FAIL;
            if(!device.profile().failed){player.displayClientMessage(Component.literal("機器は故障していません"),true);return InteractionResult.CONSUME;}
            device.repair();if(!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
            player.displayClientMessage(Component.literal("ネットワーク機器を修理しました"),true);return InteractionResult.CONSUME;
        }
        if(id.equals("network_configurator")){
            if(level.getBlockEntity(pos) instanceof NetworkDeviceEntity device){if(context.getPlayer() instanceof ServerPlayer player&&device.canUse(player))player.openMenu(device,b->b.writeBlockPos(pos));return InteractionResult.sidedSuccess(level.isClientSide);}
            if(level.getBlockState(pos).getBlock() instanceof NetworkCableBlock cable){if(!level.isClientSide&&context.getPlayer()!=null)context.getPlayer().displayClientMessage(Component.literal(cable.grade.id()+" / "+CableGrade.speed(cable.grade.bps())+" / 最大"+cable.grade.maxLength()+"ブロック / 幅4/16"),false);return InteractionResult.sidedSuccess(level.isClientSide);}
        }
        return InteractionResult.PASS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        if(id.equals("network_stability_perfect"))lines.add(Component.literal("本体拡張枠: 内部ランダム故障率を0%にする。断線は防がない。"));
        else if(id.startsWith("network_port_"))lines.add(Component.literal("ポート専用枠に装着。光接続にも対応。対向側とケーブルの上限に従う。"));
        else if(id.startsWith("network_wan_"))lines.add(Component.literal("WAN枠に装着。ONU–OLTリンクの帯域を恒久的に拡張。"));
        else if(id.equals("network_processor"))lines.add(Component.literal("本体拡張枠: 1枚ごとに内部容量4倍 (上限768Gbps)。"));
        else if(id.equals("network_repair_kit"))lines.add(Component.literal("故障した自分の機器に右クリック。1個で修理。"));
        else if(id.equals("network_configurator"))lines.add(Component.literal("機器の設定画面を開く。ケーブルの規格も調べられる。"));
        else lines.add(Component.literal("Create不要。機器GUIのガイドで使用方法を確認できます。"));
    }
}
