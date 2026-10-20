package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkGuideRenderer;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkJeiCatalog;
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

/** 実フォントで分割した説明カード。図の素材は非消費で、レシピ転送は登録しない。 */
public final class NetworkGuideCategory implements IRecipeCategory<NetworkJeiCards.GuideCard> {
    public static final RecipeType<NetworkJeiCards.GuideCard> TYPE=RecipeType.create("venusmod","network_connections",NetworkJeiCards.GuideCard.class);
    private final IDrawable icon;
    public NetworkGuideCategory(IGuiHelper helper){icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_router").get()));}
    @Override public RecipeType<NetworkJeiCards.GuideCard> getRecipeType(){return TYPE;}
    @Override public Component getTitle(){return Component.literal("ネットワーク接続ガイド");}
    @Override public IDrawable getIcon(){return icon;}
    @Override public int getWidth(){return 176;}
    @Override public int getHeight(){return 198;}
    @Override public ResourceLocation getRegistryName(NetworkJeiCards.GuideCard card){return ResourceLocation.fromNamespaceAndPath("venusmod","guide/"+card.recipe().page().id()+"/"+card.recipe().step()+"/"+card.part());}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,NetworkJeiCards.GuideCard card,IFocusGroup focuses){
        for(var node:card.recipe().page().nodes())builder.addSlot(RecipeIngredientRole.INPUT,node.x(),18+node.y()).addItemStack(NetworkGuideRenderer.icon(node));
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(NetworkJeiCards.items(NetworkJeiCatalog.ingredients(card.recipe().page())));
    }
    @Override public void draw(NetworkJeiCards.GuideCard card,IRecipeSlotsView slots,GuiGraphics g,double mouseX,double mouseY){
        var font=Minecraft.getInstance().font;var recipe=card.recipe();var page=recipe.page();var step=page.steps().get(recipe.step());
        g.drawString(font,font.plainSubstrByWidth(page.title(),172),2,2,0xff805a15,false);
        NetworkGuideRenderer.diagram(g,0,18,page,false);
        g.drawString(font,font.plainSubstrByWidth(step.title(),172),2,84,0xff303030,false);
        g.drawString(font,"接続例 / アイテム消費なし",2,96,0xff666666,false);
        for(int i=0;i<card.lines().size();i++)g.drawString(font,card.lines().get(i),2,109+i*9,0xff303030,false);
        String footer="手順 "+(recipe.step()+1)+"/"+page.steps().size()+" ・ 続き "+(card.part()+1)+"/"+card.parts();
        g.drawString(font,footer,2,185,0xff646464,false);
    }
    @Override public void getTooltip(ITooltipBuilder tooltip,NetworkJeiCards.GuideCard card,IRecipeSlotsView slots,double x,double y){
        if(y<14)tooltip.add(Component.literal(card.recipe().page().title()));
        else if(y>=81&&y<95)tooltip.add(Component.literal(card.recipe().page().steps().get(card.recipe().step()).title()));
    }
}
