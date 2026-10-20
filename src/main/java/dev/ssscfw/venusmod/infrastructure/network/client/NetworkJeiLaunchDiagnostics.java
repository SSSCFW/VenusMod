package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.VenusMod;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 検証専用。JEIランタイム開始前の画面停滞も記録し、通常プレイでは一切動かない。 */
@EventBusSubscriber(modid=VenusMod.MOD_ID, value=Dist.CLIENT)
public final class NetworkJeiLaunchDiagnostics {
    private static long started;
    private static int ticks;
    private static String previous="";
    private static boolean finished;
    private NetworkJeiLaunchDiagnostics() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(FMLLoader.isProduction()||!Boolean.getBoolean("venusmod.jeiSmokeTest")||finished)return;
        if(started==0)started=System.nanoTime();
        if(++ticks%100!=0)return;
        var minecraft=Minecraft.getInstance();
        Path output=minecraft.gameDirectory.toPath().resolve("jei-smoke");
        try {
            if(Files.exists(output.resolve("PASS.txt"))||Files.exists(output.resolve("FAIL.txt"))){finished=true;return;}
            Files.createDirectories(output);
            String screen=minecraft.screen==null?"none":minecraft.screen.getClass().getName();
            String state="screen="+screen+"\nworld="+(minecraft.level!=null)+"\noverlay="+(minecraft.getOverlay()!=null)+"\n";
            if(!state.equals(previous)){
                previous=state;Files.writeString(output.resolve("launch-state.txt"),state);
                System.out.println("JEI_SMOKE_LAUNCH "+state.replace('\n',' '));
                try(var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())){image.writeToFile(output.resolve("launch-state.png"));}
            }
            if(System.nanoTime()-started>180_000_000_000L){
                Files.writeString(output.resolve("FAIL.txt"),"JEI client did not finish within the startup budget\n"+state);
                finished=true;minecraft.stop();
            }
        } catch(Exception failure) {
            finished=true;failure.printStackTrace();minecraft.stop();
        }
    }
}
