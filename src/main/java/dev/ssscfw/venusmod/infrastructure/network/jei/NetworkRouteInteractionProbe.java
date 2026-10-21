package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkScreen;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** 隔離済みJEIスモークの続き。静的経路の操作は実クリックと既存Payloadだけを使う。 */
final class NetworkRouteInteractionProbe {
    private final Minecraft mc=Minecraft.getInstance();
    private final NetworkSmokeInput input;
    private final Path output;
    private final BiConsumer<Boolean,String> check;
    private final BlockPos position;
    private NetworkScreen screen;
    private CompletableFuture<Void> task;
    private int phase,waiting;
    private String original,oldResult;

    NetworkRouteInteractionProbe(NetworkSmokeInput input,Path output,BiConsumer<Boolean,String> check,BlockPos previous) {
        this.input=input;this.output=output;this.check=check;position=previous.north(2);
    }
    boolean tick() throws Exception {
        if(phase>1 && mc.screen!=screen)throw new IllegalStateException("Static route screen lost at "+phase);
        switch(phase){
            case 0 -> {
                mc.setScreen(null);
                server(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level=player.serverLevel();level.setBlock(position,NetworkContent.DEVICES.get(DeviceKind.ROUTER).get().defaultBlockState(),3);
                    var device=(NetworkDeviceEntity)level.getBlockEntity(position);device.setOwnerIfAbsent(player);
                    device.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
                    original=device.profile().encode();player.openMenu(device,b->b.writeBlockPos(position));
                });
            }
            case 1 -> {
                if(!ready() || !(mc.screen instanceof NetworkScreen s) || s.getMenu().kind()!=DeviceKind.ROUTER)return waiting();
                screen=(NetworkScreen)mc.screen;button("経路設定");
            }
            case 2 -> {
                check.accept(!screen.isJeiShortcutVisible(),"Route panel blocks JEI shortcut");
                edit("静的経路の宛先CIDR","bad host");edit("静的経路の次ホップ","192.168.2.2");
                oldResult=screen.getMenu().snapshot();button("経路を追加");
            }
            case 3 -> {
                check.accept(screen.getMenu().snapshot().equals(oldResult),"Bad route input not submitted");
                edit("静的経路の宛先CIDR","10.20.0.42/24");button("P0 / 北");button("経路を追加");
            }
            case 4 -> {check.accept(screen.getMenu().snapshot().contains("設定を保存"),"Route addition server response");server(()->verify(1));}
            case 5 -> {if(!ready())return waiting();button("静的経路一覧");}
            case 6 -> {
                check.accept(screen.getMenu().snapshot().contains("10.20.0.0/24 via 192.168.2.2 P1"),"Canonical route listed");
                capture("16-static-route-added.png");button("個別削除");
            }
            case 7 -> {check.accept(hasButton("削除を確定"),"Deletion requires confirmation");server(()->verify(1));capture("17-static-route-confirm.png");edit("静的経路の次ホップ","192.168.2.3");}
            case 8 -> {if(!ready())return waiting();check.accept(!hasButton("削除を確定"),"Editing gateway cancels confirmation");button("個別削除");}
            case 9 -> {GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);}
            case 10 -> {
                check.accept(!hasButton("削除を確定"),"Window resize cancels route deletion");
                check.accept(field("静的経路の宛先CIDR").getValue().equals("10.20.0.42/24")&&field("静的経路の次ホップ").getValue().equals("192.168.2.3"),"Resize preserves route drafts");
                edit("静的経路の次ホップ","192.168.2.2");button("個別削除");
            }
            case 11 -> {button("確認を中止");}
            case 12 -> {server(()->verify(1));button("ガイド");}
            case 13 -> {if(!ready())return waiting();check.accept(!screen.isJeiShortcutVisible(),"Routing guide stays modal");button("戻る");}
            case 14 -> {button("経路設定");}
            case 15 -> {check.accept(field("静的経路の次ホップ").getValue().equals("192.168.2.2"),"Route draft survives guide navigation");button("個別削除");}
            case 16 -> {button("削除を確定");}
            case 17 -> {check.accept(screen.getMenu().snapshot().contains("設定を保存"),"Confirmed route deletion server response");server(()->verify(0));}
            case 18 -> {if(!ready())return waiting();button("静的経路一覧");}
            case 19 -> {
                check.accept(screen.getMenu().snapshot().contains("静的経路 0件"),"Empty route list explicitly displayed");
                capture("18-static-route-deleted.png");Files.writeString(output.resolve("static-routes-PASS.txt"),"Native route form add/confirm/edit/cancel/resize/delete and server state verified\n");return true;
            }
            default -> throw new IllegalStateException("Unexpected route phase");
        }
        phase++;waiting=0;return false;
    }
    private void verify(int count) {
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var device=(NetworkDeviceEntity)player.serverLevel().getBlockEntity(position);
        var p=device.profile();
        if(p.routes.size()!=count || count==1 && !p.routes.getFirst().equals(new NetworkProfile.Route("10.20.0.0",24,"192.168.2.2",1)))
            throw new IllegalStateException("Incorrect static route configuration at "+phase);
        var copy=NetworkProfile.decode(p.kind,p.encode());copy.routes.clear();
        if(!copy.encode().equals(original)||device.modules.getItem(6).getCount()!=1||!device.owner().equals(player.getUUID())
                ||player.getInventory().getItem(0).getCount()!=7||!player.containerMenu.getCarried().isEmpty())
            throw new IllegalStateException("Route form changed unrelated state");
    }
    private void server(Runnable work){task=new CompletableFuture<>();mc.getSingleplayerServer().execute(()->{try{work.run();task.complete(null);}catch(Throwable e){task.completeExceptionally(e);}});}
    private boolean ready(){if(!task.isDone())return false;task.join();return true;}
    private boolean waiting(){if(++waiting>12)throw new IllegalStateException("Route fixture stalled at "+phase);return false;}
    private EditBox field(String label){return screen.children().stream().filter(w->w instanceof EditBox e&&e.visible&&e.getMessage().getString().equals(label)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private void edit(String label,String value){var e=field(label);click(e.getX()+8,e.getY()+8);input.clear();input.type(value);}
    private boolean hasButton(String label){return screen.children().stream().anyMatch(w->w instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().equals(label));}
    private void button(String label){var b=screen.children().stream().filter(w->w instanceof Button x&&x.visible&&x.active&&x.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing route button "+label+" at "+phase));click(b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2);}
    private void click(int x,int y){var w=mc.getWindow();int[] ox={0},oy={0};GLFW.glfwGetWindowPos(w.getWindow(),ox,oy);input.click(ox[0]+(int)Math.round(x*w.getGuiScale()),oy[0]+(int)Math.round(y*w.getGuiScale()));}
    private void capture(String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}}
}
