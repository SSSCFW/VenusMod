package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.CableGrade;
import java.util.List;
import net.minecraft.ChatFormatting;
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
        lines.add(Component.translatable("tooltip.venusmod.network." + id)
                .withStyle(ChatFormatting.GRAY));
    }
}
