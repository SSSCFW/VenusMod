package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.nio.file.*;
import java.util.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 開発フラグでのみ動作する実JEIスモーク。通常プレイでは生成も登録もしない。 */
final class NetworkJeiSmokeTest {
    private static boolean started;
    private final IJeiRuntime runtime;
    private final Path output;
    private int stage,ticks,checks;
    private List<NetworkJeiCards.GuideCard> guides;
    private List<NetworkJeiCards.SpecCard> specs;
    private NetworkJeiCards.GuideCard continuationSample;
    private NetworkGuideInteractionProbe interactions;
    private NetworkJeiSmokeTest(IJeiRuntime runtime){this.runtime=runtime;output=Minecraft.getInstance().gameDirectory.toPath().resolve("jei-smoke");}
    static void start(IJeiRuntime runtime){
        if(started||FMLLoader.isProduction())return;
        started=true;var probe=new NetworkJeiSmokeTest(runtime);
        Minecraft.getInstance().execute(()->NeoForge.EVENT_BUS.addListener(probe::tick));
    }
    private void require(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException(message);}
    private void tick(ClientTickEvent.Post event){
        var minecraft=Minecraft.getInstance();if(stage<0||minecraft.level==null)return;
        if(++ticks<30)return;ticks=0;
        try{
            Files.createDirectories(output);
            switch(stage++){
                case 0 -> {validate();showSpec("cat6a_cable_blue");}
                case 1 -> {capture("01-cable-spec.png");showGuide(false);}
                case 2 -> {capture("02-connection-guide.png");showGuide(true);}
                case 3 -> {capture("03-continued-instructions.png");showSpec("network_stability_perfect");}
                case 4 -> {capture("04-upgrade-spec.png");interactions=new NetworkGuideInteractionProbe(output,this::require);}
                case 5 -> {
                    if(!interactions.tick()){stage=5;break;}
                    interactions.close();
                    Files.writeString(output.resolve("PASS.txt"),"JEI runtime and native guide input verified\nchecks="+checks+"\nguideCards="+guides.size()+"\nspecCards="+specs.size()+"\nlanguage="+minecraft.options.languageCode+"\ncontinuedGuide="+continuationSample.recipe().page().id()+"/"+continuationSample.recipe().step()+"\n");stage=-1;minecraft.stop();
                }
                default -> throw new IllegalStateException("Unexpected test phase");
            }
        }catch(Throwable failure){
            stage=-1;
            if(interactions!=null)interactions.close();
            try{Files.createDirectories(output);Files.writeString(output.resolve("FAIL.txt"),failure.toString()+"\n"+Arrays.toString(failure.getStackTrace()));}catch(Exception ignored){}
            // 停止前に画面とプレイヤーを記録し、GUI不具合とfixtureの退出/死亡を区別する。
            try {
                String state = "screen=" + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getName()) + "\n";
                if (minecraft.player != null) {
                    var player = minecraft.player;
                    state += "health=" + player.getHealth() + "\nalive=" + player.isAlive()
                            + "\nposition=" + player.position() + "\nmenu=" + player.containerMenu.getClass().getName() + "\n";
                }
                Files.writeString(output.resolve("failure-state.txt"), state);
                try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("failure-state.png"));
                }
            } catch (Exception captureFailure) { failure.addSuppressed(captureFailure); }
            failure.printStackTrace();minecraft.stop();
        }
    }
    private void validate(){
        var manager=runtime.getRecipeManager();var font=Minecraft.getInstance().font;
        guides=manager.createRecipeLookup(NetworkGuideCategory.TYPE).get().toList();
        specs=manager.createRecipeLookup(NetworkSpecCategory.TYPE).get().toList();
        require(guides.size()==NetworkJeiCards.guides().size(),"Connection registration count");
        require(specs.size()==NetworkJeiCards.specifications().size(),"Spec registration count");
        require(guides.size()>=NetworkGuide.recipes().size(),"No lost guide steps");
        require(guides.stream().anyMatch(c->c.parts()>1),"Long text must produce continuation cards");
        // 折返し数は実フォントで変わる。特定トピックが必ず複数カードになるとは仮定しない。
        continuationSample=guides.stream().filter(c->c.parts()>1&&c.part()==0)
                .max(Comparator.comparingInt(NetworkJeiCards.GuideCard::parts)).orElseThrow();
        for(var catalog:NetworkJeiCatalog.specs())for(var item:NetworkJeiCards.items(catalog.items())){
            var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,item);
            var found=manager.createRecipeLookup(NetworkSpecCategory.TYPE).limitFocus(List.of(focus)).get().toList();
            require(!found.isEmpty(),"No item spec: "+item);
            if(!catalog.id().equals("network_configurator"))require(found.stream().allMatch(c->c.spec().id().equals(catalog.id())),"Unrelated item specs: "+item);
            var related=manager.createRecipeLookup(NetworkGuideCategory.TYPE).limitFocus(List.of(focus)).get().toList();
            require(!related.isEmpty(),"No usage guide: "+item);
        }
        var empty=runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        for(var card:guides){
            require(card.lines().size()<=NetworkJeiCards.BODY_ROWS&&card.lines().stream().allMatch(s->font.width(s)<=NetworkJeiCards.TEXT_WIDTH),"Connection text bounds");
            require(manager.createRecipeLayoutDrawable(manager.getRecipeCategory(NetworkGuideCategory.TYPE),card,empty).isPresent(),"JEI connection layout failed");
        }
        for(var card:specs){
            require(card.lines().size()<=NetworkJeiCards.BODY_ROWS&&card.lines().stream().allMatch(s->font.width(s)<=NetworkJeiCards.TEXT_WIDTH),"Spec text bounds");
            require(manager.createRecipeLayoutDrawable(manager.getRecipeCategory(NetworkSpecCategory.TYPE),card,empty).isPresent(),"JEI spec layout failed");
        }
    }
    private void showSpec(String id){
        var item=NetworkJeiCards.items(List.of(id)).getFirst();
        var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,item);
        var cards=runtime.getRecipeManager().createRecipeLookup(NetworkSpecCategory.TYPE).limitFocus(List.of(focus)).get().toList();
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(NetworkSpecCategory.TYPE),cards,List.of(focus));
    }
    private void showGuide(boolean continuation){
        var card=guides.stream().filter(c->c.recipe().equals(continuationSample.recipe())
                && c.part()==(continuation?continuationSample.parts()-1:0)).findFirst().orElseThrow();
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(NetworkGuideCategory.TYPE),List.of(card),List.of());
    }
    private void capture(String name)throws Exception{
        var mc=Minecraft.getInstance();require(mc.screen!=null&&mc.screen.getClass().getName().startsWith("mezz.jei."),"JEI screen was not displayed");
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}
    }
}
