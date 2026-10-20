package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 全16色を同じ規格カードに関連付ける。数量付き材料やクラフト出力は表示しない。 */
public final class NetworkSpecCategory implements IRecipeCategory<NetworkJeiCards.SpecCard> {
    public static final RecipeType<NetworkJeiCards.SpecCard> TYPE=RecipeType.create("venusmod","network_specs",NetworkJeiCards.SpecCard.class);
    private final IDrawable icon;
    public NetworkSpecCategory(IGuiHelper helper){icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_configurator").get()));}
    @Override public RecipeType<NetworkJeiCards.SpecCard> getRecipeType(){return TYPE;}
    @Override public Component getTitle(){return Component.literal("ネットワーク機器・配線の仕様");}
    @Override public IDrawable getIcon(){return icon;}
    @Override public int getWidth(){return 176;}
    @Override public int getHeight(){return 150;}
    @Override public ResourceLocation getRegistryName(NetworkJeiCards.SpecCard card){return ResourceLocation.fromNamespaceAndPath("venusmod","spec/"+card.spec().id()+"/"+card.part());}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,NetworkJeiCards.SpecCard card,IFocusGroup focuses){
        builder.addSlot(RecipeIngredientRole.INPUT,4,18).addItemStacks(NetworkJeiCards.items(card.spec().items()));
    }
    @Override public void draw(NetworkJeiCards.SpecCard card,IRecipeSlotsView slots,GuiGraphics g,double mouseX,double mouseY){
        var font=Minecraft.getInstance().font;
        g.drawString(font,font.plainSubstrByWidth(card.spec().title(),172),2,2,0xff805a15,false);
        g.fill(2,16,22,36,0xffc6b078);g.fill(3,17,21,35,0xfff0e9d9);
        g.drawString(font,"仕様・使い方（非消費）",27,18,0xff404040,false);
        g.drawString(font,"製作はR / 関連手順はU",27,29,0xff666666,false);
        for(int i=0;i<card.lines().size();i++)g.drawString(font,card.lines().get(i),2,46+i*9,0xff303030,false);
        g.drawString(font,"説明 "+(card.part()+1)+" / "+card.parts(),2,135,0xff646464,false);
    }
    @Override public void getTooltip(ITooltipBuilder tooltip,NetworkJeiCards.SpecCard card,IRecipeSlotsView slots,double x,double y){if(y<14)tooltip.add(Component.literal(card.spec().title()));}
}
