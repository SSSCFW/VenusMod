package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkScreen;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

/** 隔離済みのJEI入力試験を継続し、通常ケーブルとCommand Payload経由の診断を確認する。 */
final class NetworkDiagnosticsInteractionProbe {
    private final Minecraft mc = Minecraft.getInstance();
    private final NetworkScreen screen;
    private final net.minecraft.server.MinecraftServer server;
    private final java.util.UUID playerId;
    private final NetworkSmokeInput input;
    private final Path output;
    private final BiConsumer<Boolean,String> check;
    private final BlockPos source;
    private int phase, waiting;
    private CompletableFuture<Void> task;
    private String savedProfile, oldResult;

    NetworkDiagnosticsInteractionProbe(NetworkScreen screen, NetworkSmokeInput input, Path output,
                                       BiConsumer<Boolean,String> check, BlockPos source) {
        this.server=mc.getSingleplayerServer();this.playerId=mc.player.getUUID();
        this.screen=screen;this.input=input;this.output=output;this.check=check;this.source=source;
    }
    boolean tick() throws Exception {
        if (mc.screen != screen) throw new IllegalStateException("Unexpected diagnostics screen at " + phase);
        switch(phase) {
            case 0 -> {
                button("戻る");
                server(() -> {
                    var player=server.getPlayerList().getPlayer(playerId);
                    var level=player.serverLevel();
                    var device=(NetworkDeviceEntity)level.getBlockEntity(source);
                    device.profile().address="10.166.0.10";device.profile().dns="10.166.0.20";device.refreshState();
                    BlockPos other=source.east(4);
                    level.setBlock(other,NetworkContent.DEVICES.get(DeviceKind.SERVER).get().defaultBlockState(),3);
                    var destination=(NetworkDeviceEntity)level.getBlockEntity(other);
                    destination.profile().address="10.166.0.20";destination.profile().name="diagnostic-server";
                    destination.profile().dnsRecords.put("diag.local","10.166.0.20");
                    destination.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
                    destination.setOwnerIfAbsent(player);destination.refreshState();
                    for(int n=1;n<4;n++) {
                        var p=source.east(n);var cable=NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
                        level.setBlock(p,Block.updateFromNeighbourShapes(cable.defaultBlockState(),level,p),3);
                    }
                    savedProfile=device.profile().encode();
                });
            }
            case 1 -> {if(!ready())return false;button("診断");}
            case 2 -> {
                check.accept(!screen.isJeiShortcutVisible(),"No JEI click area behind diagnostics");
                edit("診断の宛先","bad host");edit("診断の転送量MB","0");oldResult=screen.getMenu().snapshot();button("Ping");
            }
            case 3 -> {
                check.accept(screen.getMenu().snapshot().equals(oldResult),"Rejected diagnostics did not submit a command");
                capture("10-diagnostics-invalid.png");edit("診断の宛先","10.166.0.20");button("Ping");
            }
            case 4 -> {
                check.accept(screen.getMenu().snapshot().contains("Reply from 10.166.0.20"),"Native Ping button used real cable and server response");
                button("経路確認");
            }
            case 5 -> {
                check.accept(screen.getMenu().snapshot().contains("diagnostic-server"),"Trace button displayed route device names");
                edit("診断の宛先","diag.local");button("名前解決");
            }
            case 6 -> {
                check.accept(screen.getMenu().snapshot().contains("10.166.0.20"),"DNS button received configured authority answer");
                edit("診断の転送量MB","1000000");button("normal");button("転送開始");
            }
            case 7 -> {
                check.accept(screen.getMenu().snapshot().contains("開始"),"Transfer button received server start result");
                server(() -> checkTransfer(1));
            }
            case 8 -> {if(!ready())return false;button("転送一覧");}
            case 9 -> {
                check.accept(screen.getMenu().snapshot().contains("high") && screen.getMenu().snapshot().contains("最終割当"),"Flow list includes requested priority and bandwidth");
                capture("11-diagnostics-transfer.png");edit("診断の転送量MB","0");button("転送開始");
            }
            case 10 -> {
                server(() -> checkTransfer(1));
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);
            }
            case 11 -> {
                if(!ready())return false;
                check.accept(field("診断の宛先").getValue().equals("diag.local") && field("診断の転送量MB").getValue().equals("0"),"Diagnostics drafts survived native resize");
                relative(290,243);relative(154,138);input.key(KeyEvent.VK_ENTER);
            }
            case 12 -> {
                check.accept(!screen.isJeiShortcutVisible() && mc.player.containerMenu.getCarried().isEmpty(),"Diagnostics blocked hidden JEI/inventory clicks");
                button("ガイド");
            }
            case 13 -> {button("戻る");}
            case 14 -> {
                check.accept(field("仮想CLI").getValue().equals("show"),"Diagnostics did not edit CLI draft");button("診断");
            }
            case 15 -> {
                check.accept(field("診断の宛先").getValue().equals("diag.local"),"Diagnostics draft survived guide round trip");
                server(() -> server.getPlayerList().getPlayer(playerId).serverLevel().destroyBlock(source.east(1),false));
            }
            case 16 -> {if(!ready())return false;button("Ping");}
            case 17 -> {
                check.accept(!screen.getMenu().snapshot().contains("Reply from"),"Disconnected diagnostic no longer reported success");
                capture("12-diagnostics-disconnected.png");
                server(() -> {
                    var player=server.getPlayerList().getPlayer(playerId);
                    var device=(NetworkDeviceEntity)player.serverLevel().getBlockEntity(source);
                    if(!savedProfile.equals(device.profile().encode()) || device.modules.getItem(6).getCount()!=1
                            || player.getInventory().getItem(0).getCount()!=7 || !player.containerMenu.getCarried().isEmpty())
                        throw new IllegalStateException("Diagnostics changed settings/modules/inventory");
                });
            }
            case 18 -> {
                if(!ready())return false;
                check.accept(true,"Server configuration and inventory unchanged by diagnostics");
                Files.writeString(output.resolve("diagnostics-PASS.txt"),"Native diagnostics inputs, real cable transport, transfer and modal checks passed\n");
                return true;
            }
            default -> throw new IllegalStateException("Diagnostics phase " + phase);
        }
        phase++;waiting=0;return false;
    }
    private void checkTransfer(int count) {
        var player=server.getPlayerList().getPlayer(playerId);
        var device=(NetworkDeviceEntity)player.serverLevel().getBlockEntity(source);
        var flows=NetworkWorld.get(player.serverLevel()).simulator().flows().stream().filter(f->f.source.equals(device.id())).toList();
        if(flows.size()!=count || flows.stream().anyMatch(f->f.priority()!=TransferPriority.HIGH || f.totalBytes!=1_000_000_000_000L || f.remaining>=f.totalBytes))
            throw new IllegalStateException("Native transfer created incorrect progress/count/priority");
    }
    private void server(Runnable operation) {
        task=new CompletableFuture<>();server.execute(() -> {try{operation.run();task.complete(null);}catch(Throwable t){task.completeExceptionally(t);}});
    }
    private boolean ready() {if(!task.isDone()){if(++waiting>12)throw new IllegalStateException("Server fixture stalled");return false;}task.join();return true;}
    private EditBox field(String name) {return screen.children().stream().filter(w->w instanceof EditBox e&&e.visible&&e.getMessage().getString().equals(name)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private void edit(String name,String value) {var e=field(name);click(e.getX()+8,e.getY()+8);input.clear();input.type(value);}
    private void button(String name) {
        var b=screen.children().stream().filter(w->w instanceof Button x&&x.visible&&x.active&&x.getMessage().getString().equals(name)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing diagnostics button "+name+" at "+phase));
        click(b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2);
    }
    private void relative(int x,int y) {click((screen.width-320)/2+x,(screen.height-252)/2+y);}
    private void click(int x,int y) {var w=mc.getWindow();int[] ox={0},oy={0};GLFW.glfwGetWindowPos(w.getWindow(),ox,oy);input.click(ox[0]+(int)Math.round(x*w.getGuiScale()),oy[0]+(int)Math.round(y*w.getGuiScale()));}
    private void capture(String name) throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}}
}
