package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import mezz.jei.api.*;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEIだけが発見する任意プラグイン。本体からこのクラスを参照しない。 */
@JeiPlugin
public final class NetworkJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("venusmod","network_guide");}
    @Override public void registerRecipes(IRecipeRegistration registration){
        Component[] common=NetworkCli.guide().stream().map(Component::literal).toArray(Component[]::new);
        for(var entry:NetworkContent.NETWORK_ITEMS.entrySet()) {
            registration.addItemStackInfo(new ItemStack(entry.getValue().get()),common);
        }
        for(var grade:CableGrade.values())registration.addItemStackInfo(new ItemStack(NetworkContent.CABLES.get(grade).get()),
                Component.literal("接続: 機器ポート → ケーブル → 機器ポート"),
                Component.literal("上限: "+CableGrade.speed(grade.bps())+" / "+grade.maxLength()+"ブロック / 太さ4/16"),
                Component.literal(grade.optical()?"両端の port N media fiber を設定。ONU/OLTの北ポート以外は10G以上の光対応モジュールが必要です。":"銅線ポート (port N media copper) に接続します。"),
                Component.literal("途中でT字分岐すると通信できません。分岐にはL2スイッチを置いてください。異なるケーブル種類を直接つなぐ場合も中継機器が必要です。"));
    }
}
