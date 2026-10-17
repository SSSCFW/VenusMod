package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkGuideRenderer;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkGuide;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** クラフトではなく、関連機器から辿れる接続例。1手順1カードで長文の切れを避ける。 */
public final class NetworkGuideCategory implements IRecipeCategory<NetworkGuide.Recipe> {
    public static final RecipeType<NetworkGuide.Recipe> TYPE=RecipeType.create("venusmod","network_connections",NetworkGuide.Recipe.class);
    private final IDrawable icon;
    public NetworkGuideCategory(IGuiHelper helper){icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_router").get()));}
    @Override public RecipeType<NetworkGuide.Recipe> getRecipeType(){return TYPE;}
    @Override public Component getTitle(){return Component.literal("ネットワーク接続ガイド");}
    @Override public IDrawable getIcon(){return icon;}
    @Override public int getWidth(){return 176;}
    @Override public int getHeight(){return 196;}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,NetworkGuide.Recipe recipe,IFocusGroup focuses){
        for(var node:recipe.page().nodes())builder.addSlot(RecipeIngredientRole.INPUT,node.x(),18+node.y()).addItemStack(NetworkGuideRenderer.icon(node));
    }
    @Override public void draw(NetworkGuide.Recipe recipe,IRecipeSlotsView slots,GuiGraphics g,double mouseX,double mouseY){
        var font=Minecraft.getInstance().font;var page=recipe.page();var step=page.steps().get(recipe.step());
        g.drawString(font,page.title(),2,2,0xffd2a143,false);
        NetworkGuideRenderer.diagram(g,0,18,page,false);
        int y=84;g.drawString(font,step.title(),2,y,0xff3c3c3c,false);y+=12;
        for(String line:step.lines())for(var part:font.split(Component.literal(line),172)){g.drawString(font,part,2,y,0xff303030,false);y+=9;}
        g.drawString(font,"手順 "+(recipe.step()+1)+" / "+page.steps().size(),2,185,0xff646464,false);
    }
}
