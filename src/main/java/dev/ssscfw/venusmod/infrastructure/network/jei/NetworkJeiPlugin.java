package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkScreen;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkJeiCatalog;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEIだけが発見する任意プラグイン。本体からJEIクラスを参照しない。 */
@JeiPlugin
public final class NetworkJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("venusmod","network_guide");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){
        var helper=registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new NetworkGuideCategory(helper),new NetworkSpecCategory(helper));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        // 全機器を触媒にすると、U検索で無関係な全手順が返る。全一覧への入口は診断ツールだけ。
        registration.addRecipeCatalyst(new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_configurator").get()),NetworkGuideCategory.TYPE,NetworkSpecCategory.TYPE);
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        registration.addRecipes(NetworkGuideCategory.TYPE,NetworkJeiCards.guides());
        registration.addRecipes(NetworkSpecCategory.TYPE,NetworkJeiCards.specifications());
        for(var spec:NetworkJeiCatalog.specs()){
            var items=NetworkJeiCards.items(spec.items());if(items.isEmpty())continue;
            Component[] lines=spec.lines().stream().map(Component::literal).toArray(Component[]::new);
            registration.addItemStackInfo(items,lines);
        }
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registration.addGuiContainerHandler(NetworkScreen.class,new IGuiContainerHandler<NetworkScreen>(){
            @Override public Collection<IGuiClickableArea> getGuiClickableAreas(NetworkScreen screen,double x,double y){
                if(!screen.isJeiShortcutVisible())return List.of();
                return List.of(IGuiClickableArea.createBasic(270,237,42,12,NetworkGuideCategory.TYPE,NetworkSpecCategory.TYPE));
            }
        });
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){
        if(Boolean.getBoolean("venusmod.jeiSmokeTest"))NetworkJeiSmokeTest.start(runtime);
    }
}
