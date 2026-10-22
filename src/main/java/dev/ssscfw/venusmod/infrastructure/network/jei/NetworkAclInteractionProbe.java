package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.client.NetworkScreen;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import org.lwjgl.glfw.GLFW;

/** 隔離済みJEIスモークのACL操作。利用者の操作はRobotと既存Payloadを通す。 */
final class NetworkAclInteractionProbe {
    private final Minecraft mc=Minecraft.getInstance();
    private final MinecraftServer server;
    private final UUID playerId;
    private final NetworkSmokeInput input;
    private final Path output;
    private final BiConsumer<Boolean,String> check;
    private final BlockPos position;
    private NetworkScreen screen;
    private CompletableFuture<Void> task;
    private int phase,waiting;
    private String original,oldResult;

    NetworkAclInteractionProbe(NetworkSmokeInput input,Path output,BiConsumer<Boolean,String> check,BlockPos previous){
        this.input=input;this.output=output;this.check=check;position=previous.south(2);
        server=mc.getSingleplayerServer();playerId=mc.player.getUUID();
    }
    boolean tick()throws Exception{
        if(phase>1&&mc.screen!=screen)throw new IllegalStateException("ACL screen lost at "+phase);
        switch(phase){
            case 0 -> {
                mc.setScreen(null);
                server(()->{
                    var player=server.getPlayerList().getPlayer(playerId);var level=player.serverLevel();
                    level.setBlock(position,NetworkContent.DEVICES.get(DeviceKind.ROUTER).get().defaultBlockState(),3);
                    var device=(NetworkDeviceEntity)level.getBlockEntity(position);device.setOwnerIfAbsent(player);
                    device.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
                    // モーダル背後の実スロットをクリックし、数字キーによる交換も検出できる配置。
                    player.getInventory().setItem(9,new ItemStack(Items.EMERALD,3));
                    original=device.profile().encode();player.openMenu(device,b->b.writeBlockPos(position));
                });
            }
            case 1 -> {
                if(!ready()||!(mc.screen instanceof NetworkScreen s)||s.getMenu().kind()!=DeviceKind.ROUTER)return waiting();
                screen=(NetworkScreen)mc.screen;button("ACL設定");
            }
            case 2 -> {
                check.accept(!screen.isJeiShortcutVisible(),"ACL form blocks JEI shortcut");draftPreserved();
                edit("ACL送信元","bad host");oldResult=screen.getMenu().snapshot();button("規則を追加");
            }
            case 3 -> {
                check.accept(screen.getMenu().snapshot().equals(oldResult),"Invalid ACL input was not submitted");
                edit("ACL送信元","10.20.0.42/24");button("規則を追加");
            }
            case 4 -> {check.accept(screen.getMenu().snapshot().contains("設定を保存"),"Native ACL addition received server response");server(()->verify(1,true));}
            case 5 -> {if(!ready())return waiting();button("ACL一覧");}
            case 6 -> {
                check.accept(screen.getMenu().snapshot().contains("P0 in #10 permit ip 10.20.0.0/24"),"ACL list displays canonical source and selected rule");
                capture("19-acl-added.png");button("個別削除");
            }
            case 7 -> {
                check.accept(hasButton("削除を確定"),"First ACL deletion click requires confirmation");
                server(()->verify(1,true));capture("20-acl-confirm.png");edit("ACL順序番号","11");
            }
            case 8 -> {
                if(!ready())return waiting();check.accept(!hasButton("削除を確定"),"Editing ACL sequence cancels confirmation");
                edit("ACL順序番号","10");button("個別削除");
            }
            case 9 -> {button("確認を中止");}
            case 10 -> {check.accept(!hasButton("削除を確定"),"Explicit ACL cancellation is local");button("個別削除");}
            case 11 -> {GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1152,768);}
            case 12 -> {
                check.accept(!hasButton("削除を確定"),"Native resize cancels ACL confirmation");draftPreserved();button("個別削除");
            }
            case 13 -> {button("ガイド");}
            case 14 -> {check.accept(!screen.isJeiShortcutVisible(),"ACL guide stays modal");button("戻る");}
            case 15 -> {button("ACL設定");}
            case 16 -> {
                check.accept(!hasButton("削除を確定"),"Guide navigation cancels ACL confirmation");draftPreserved();
                relative(290,243);relative(86,166);input.type("1");
            }
            case 17 -> {
                check.accept(!screen.isJeiShortcutVisible()&&mc.player.containerMenu.getCarried().isEmpty(),"ACL blocks hidden JEI, inventory pickup and hotbar swapping");
                server(()->verify(1,true));button("個別削除");
            }
            case 18 -> {
                if(!ready())return waiting();
                check.accept(hasButton("削除を確定"),"ACL expected contents captured before concurrent replacement");
                server(this::replaceRule);
            }
            case 19 -> {if(!ready())return waiting();button("削除を確定");}
            case 20 -> {
                check.accept(screen.getMenu().snapshot().contains("内容が変わりました"),"Stale native confirmation rejected after rule replacement");
                server(()->verify(1,false));
            }
            case 21 -> {if(!ready())return waiting();button("動作: 許可");button("個別削除");}
            case 22 -> {
                check.accept(hasButton("動作: 拒否")&&hasButton("削除を確定"),"New confirmation uses the replacement rule contents");button("削除を確定");
            }
            case 23 -> {check.accept(screen.getMenu().snapshot().contains("設定を保存"),"Matching ACL deletion received server response");server(()->verify(0,false));}
            case 24 -> {if(!ready())return waiting();button("ACL一覧");}
            case 25 -> {
                check.accept(screen.getMenu().snapshot().contains("ACL未適用 (すべて許可)"),"Last rule removal explicitly restores unrestricted direction");
                capture("21-acl-deleted.png");server(()->verify(0,false));
            }
            case 26 -> {
                if(!ready())return waiting();check.accept(true,"ACL native operations preserve server configuration, ownership, modules and inventory");
                Files.writeString(output.resolve("acl-PASS.txt"),"Native ACL add/list/confirm/edit/cancel/resize/guide, concurrent replacement rejection and exact deletion verified\n");return true;
            }
            default -> throw new IllegalStateException("Unexpected ACL phase");
        }
        phase++;waiting=0;return false;
    }
    private void draftPreserved(){
        String expectedSource=phase==2?"any":"10.20.0.42/24";
        check.accept(field("ACL順序番号").getValue().equals("10")&&field("ACL送信元").getValue().equals(expectedSource)
            &&field("ACL宛先").getValue().equals("any")&&field("ACL宛先ポート").getValue().equals("any")
            &&hasButton("P0 / 北")&&hasButton("方向: in (受信)")&&hasButton("動作: 許可")&&hasButton("プロトコル: IP"),"ACL draft fields and selections preserved at "+phase);
    }
    private NetworkPolicy.Rule rule(boolean permit){
        return new NetworkPolicy.Rule(0,NetworkPolicy.Direction.IN,10,permit,NetworkPolicy.Protocol.IP,
            NetworkPolicy.Prefix.parse("10.20.0.0/24"),NetworkPolicy.Prefix.parse("any"),-1);
    }
    /** 別編集の競合だけをfixtureで再現する。利用者自身の追加・削除はGUI経由で実行する。 */
    private void replaceRule(){
        var player=server.getPlayerList().getPlayer(playerId);var level=player.serverLevel();
        var device=(NetworkDeviceEntity)level.getBlockEntity(position);var simulator=NetworkWorld.get(level).simulator();
        var removed=NetworkServiceCommands.run(simulator,device.id(),"acl delete 0 in 10");
        var added=NetworkServiceCommands.run(simulator,device.id(),"acl add 0 in 10 deny ip 10.20.0.0/24 any");
        if(!removed.changed()||!added.changed())throw new IllegalStateException("ACL concurrent replacement fixture failed");
        device.adoptProfile(simulator.nodes().get(device.id()));device.refreshState();verify(1,false);
    }
    private void verify(int count,boolean permit){
        var player=server.getPlayerList().getPlayer(playerId);var device=(NetworkDeviceEntity)player.serverLevel().getBlockEntity(position);
        var profile=device.profile();var rules=profile.services.policy.rules();
        if(rules.size()!=count||count==1&&!rules.getFirst().equals(rule(permit)))throw new IllegalStateException("Incorrect ACL rule at "+phase);
        var copy=NetworkProfile.decode(profile.kind,profile.encode());copy.services.policy.clear(0,NetworkPolicy.Direction.IN);
        var diamonds=player.getInventory().getItem(0);var emeralds=player.getInventory().getItem(9);
        if(!copy.encode().equals(original)||device.modules.getItem(6).getCount()!=1||!device.owner().equals(playerId)
            ||!diamonds.is(Items.DIAMOND)||diamonds.getCount()!=7||!emeralds.is(Items.EMERALD)||emeralds.getCount()!=3
            ||!player.containerMenu.getCarried().isEmpty())throw new IllegalStateException("ACL form changed unrelated state at "+phase);
    }
    private void server(Runnable work){task=new CompletableFuture<>();server.execute(()->{try{work.run();task.complete(null);}catch(Throwable e){task.completeExceptionally(e);}});}
    private boolean ready(){if(!task.isDone())return false;task.join();return true;}
    private boolean waiting(){if(++waiting>12)throw new IllegalStateException("ACL fixture stalled at "+phase);return false;}
    private EditBox field(String label){return screen.children().stream().filter(w->w instanceof EditBox e&&e.visible&&e.getMessage().getString().equals(label)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private void edit(String label,String value){var e=field(label);click(e.getX()+8,e.getY()+8);input.clear();input.type(value);}
    private boolean hasButton(String label){return screen.children().stream().anyMatch(w->w instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().equals(label));}
    private void button(String label){var b=screen.children().stream().filter(w->w instanceof Button x&&x.visible&&x.active&&x.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing ACL button "+label+" at "+phase));click(b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2);}
    private void relative(int x,int y){click((screen.width-320)/2+x,(screen.height-252)/2+y);}
    private void click(int x,int y){var w=mc.getWindow();int[] ox={0},oy={0};GLFW.glfwGetWindowPos(w.getWindow(),ox,oy);input.click(ox[0]+(int)Math.round(x*w.getGuiScale()),oy[0]+(int)Math.round(y*w.getGuiScale()));}
    private void capture(String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}}
}
