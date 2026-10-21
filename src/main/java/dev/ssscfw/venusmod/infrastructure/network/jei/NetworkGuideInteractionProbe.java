package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkScreen;
import dev.ssscfw.venusmod.infrastructure.network.core.DeviceKind;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** 専用JEIスモークからだけ呼ぶ実GUI入力検査。Robotは隔離したXvfb上のゲームウィンドウを操作する。 */
final class NetworkGuideInteractionProbe implements AutoCloseable {
    private final Minecraft minecraft=Minecraft.getInstance();
    private final Path output;
    private final BiConsumer<Boolean,String> check;
    private final NetworkSmokeInput input;
    private int phase,waiting;
    private NetworkScreen screen;
    private CompletableFuture<Void> setup,verified;
    private BlockPos position;
    private String savedProfile;

    NetworkGuideInteractionProbe(Path output,BiConsumer<Boolean,String> check)throws Exception{
        this.output=output;this.check=check;
        if(!output.toAbsolutePath().normalize().getParent().getFileName().toString().equals("jeiSmokeClient")
                ||!Files.readString(output.resolve("fixture.txt")).trim().equals("jei-smoke-world")
                ||minecraft.getSingleplayerServer()==null)throw new IllegalStateException("Not an isolated guide fixture");
        input=new NetworkSmokeInput(minecraft.gameDirectory.toPath().resolve("../../tools/verification/JeiNativeInput.java").normalize(),output.resolve("native-input.log"));
        GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
    }
    /** 呼出し間に実ゲームのイベントループが進み、ネイティブ入力を処理する。 */
    boolean tick()throws Exception{
        switch(phase){
            case 0 -> {
                minecraft.setScreen(null);
                var server=minecraft.getSingleplayerServer();var playerId=minecraft.player.getUUID();
                setup=new CompletableFuture<>();
                server.execute(()->{try{
                    var player=server.getPlayerList().getPlayer(playerId);
                    if(player==null)throw new IllegalStateException("Fixture player missing");
                    position=player.blockPosition().east(2);
                    player.serverLevel().setBlock(position,NetworkContent.DEVICES.get(DeviceKind.PC).get().defaultBlockState(),3);
                    var device=(NetworkDeviceEntity)player.serverLevel().getBlockEntity(position);
                    device.setOwnerIfAbsent(player);
                    device.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_processor").get()));
                    device.modules.setItem(7,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
                    player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));
                    savedProfile=device.profile().encode();
                    player.openMenu(device,buffer->buffer.writeBlockPos(position));setup.complete(null);
                }catch(Throwable failure){setup.completeExceptionally(failure);}});
            }
            case 1 -> {
                if(!setup.isDone()||!(minecraft.screen instanceof NetworkScreen))return waitForMenu();
                setup.join();screen=(NetworkScreen)minecraft.screen;
                check.accept(screen.isJeiShortcutVisible(),"JEI shortcut exists in the real device console");
                clickEdit("仮想CLI");type("show");clickButton("ガイド");
            }
            case 2 -> {
                sameScreen();check.accept(!screen.isJeiShortcutVisible(),"No JEI area behind guide article");clickButton("目次・検索");
            }
            case 3 -> {sameScreen();clickEdit("ガイド検索");type("vlan");}
            case 4 -> {
                sameScreen();check.accept(search().getValue().equals("vlan"),"Native keyboard reaches guide query");
                check.accept(hasButton("VLANでLANを分離"),"VLAN title is a clickable result");
                capture("05-guide-search.png");clickRelative(290,243);
            }
            case 5 -> {
                sameScreen();check.accept(search().getValue().equals("vlan"),"Hidden JEI area leaves query untouched");
                check.accept(minecraft.player.containerMenu.getCarried().isEmpty(),"No inventory pickup behind guide index");
                clickEdit("ガイド検索");clear();type("zz_no_matching_help");
            }
            case 6 -> {check.accept(!hasButton("VLANでLANを分離"),"No stale result after zero matches");clickRelative(154,138);key(KeyEvent.VK_ENTER);}
            case 7 -> {
                sameScreen();check.accept(search().getValue().equals("zz_no_matching_help"),"Enter with zero matches does not open a topic");
                capture("06-guide-empty.png");clickEdit("ガイド検索");clear();type("vlan");
            }
            case 8 -> {GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(),1152,768);}
            case 9 -> {
                sameScreen();check.accept(search().getValue().equals("vlan"),"Native window resize preserves query");
                check.accept(hasButton("VLANでLANを分離"),"Results survive reinitialization");clickButton("VLANでLANを分離");
            }
            case 10 -> {
                sameScreen();check.accept(!searchVisible(),"Result click opens article instead of console");
                check.accept(hasButton("目次・検索"),"Article navigation remains available");
                capture("07-guide-article.png");clickButton("目次・検索");
            }
            case 11 -> {check.accept(search().getValue().equals("vlan"),"Returning to index preserves query");clickButton("全項目");}
            case 12 -> {
                check.accept(hasButton("この機器"),"Machine filter toggles through native click");capture("08-guide-related.png");key(KeyEvent.VK_ESCAPE);
            }
            case 13 -> {sameScreen();check.accept(!searchVisible()&&hasButton("目次・検索"),"Escape returns from index to article");key(KeyEvent.VK_ESCAPE);}
            case 14 -> {
                sameScreen();check.accept(screen.isJeiShortcutVisible(),"Escape returns from article to console");
                check.accept(edit("仮想CLI").getValue().equals("show"),"Search and resize do not edit or submit CLI draft");
                clickRelative(290,243);
            }
            case 15 -> {
                check.accept(minecraft.screen!=null&&minecraft.screen.getClass().getName().startsWith("mezz.jei."),"Real mouse click opens registered JEI categories");
                capture("09-jei-from-device.png");key(KeyEvent.VK_ESCAPE);
            }
            case 16 -> {sameScreen();check.accept(screen.isJeiShortcutVisible(),"JEI back returns to the same device menu");clickButton("設定");}
            case 17 -> {sameScreen();check.accept(!screen.isJeiShortcutVisible(),"No JEI area behind settings form");clickRelative(290,243);}
            case 18 -> {
                sameScreen();check.accept(!screen.isJeiShortcutVisible()&&hasButton("適用"),"Hidden JEI click does not leave settings");
                verified=new CompletableFuture<>();var server=minecraft.getSingleplayerServer();var playerId=minecraft.player.getUUID();
                server.execute(()->{try{
                    var player=server.getPlayerList().getPlayer(playerId);
                    var device=(NetworkDeviceEntity)player.serverLevel().getBlockEntity(position);
                    if(!savedProfile.equals(device.profile().encode())||device.modules.getItem(6).getCount()!=1
                            ||player.getInventory().getItem(0).getCount()!=7||!player.containerMenu.getCarried().isEmpty())
                        throw new IllegalStateException("Guide input changed server settings or inventory");
                    verified.complete(null);
                }catch(Throwable failure){verified.completeExceptionally(failure);}});
            }
            case 19 -> {
                if(!verified.isDone())return waitForMenu();verified.join();check.accept(true,"Server settings and inventory unchanged by navigation");
                Files.writeString(output.resolve("interaction-PASS.txt"),"Native mouse/key/resize and server-menu checks passed\n");return true;
            }
            default -> throw new IllegalStateException("Unexpected guide test phase");
        }
        phase++;waiting=0;return false;
    }
    private boolean waitForMenu(){if(++waiting>12)throw new IllegalStateException("Guide test phase stalled: "+phase);return false;}
    private void sameScreen(){check.accept(minecraft.screen==screen,"Expected original NetworkScreen at phase "+phase);}
    private boolean hasButton(String text){return screen.children().stream().anyMatch(w->w instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().strip().replaceFirst("^> ","").equals(text));}
    private void clickButton(String text){
        var button=screen.children().stream().filter(w->w instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().strip().replaceFirst("^> ","").equals(text)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing button "+text+" at "+phase));
        click(button.getX()+button.getWidth()/2,button.getY()+button.getHeight()/2);
    }
    private EditBox edit(String title){return screen.children().stream().filter(w->w instanceof EditBox e&&e.visible&&e.getMessage().getString().equals(title)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private EditBox search(){return edit("ガイド検索");}
    private boolean searchVisible(){return screen.children().stream().anyMatch(w->w instanceof EditBox e&&e.visible&&e.getMessage().getString().equals("ガイド検索"));}
    private void clickEdit(String title){var edit=edit(title);click(edit.getX()+10,edit.getY()+8);}
    private void clickRelative(int x,int y){click((screen.width-320)/2+x,(screen.height-252)/2+y);}
    private void click(int guiX,int guiY){
        var window=minecraft.getWindow();int[] x={0},y={0};GLFW.glfwGetWindowPos(window.getWindow(),x,y);
        input.click(x[0]+(int)Math.round(guiX*window.getGuiScale()),y[0]+(int)Math.round(guiY*window.getGuiScale()));
    }
    private void key(int code){input.key(code);}
    private void clear(){input.clear();}
    private void type(String text){input.type(text);}
    @Override public void close(){input.close();}
    private void capture(String name)throws Exception{try(var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())){image.writeToFile(output.resolve(name));}}
}
