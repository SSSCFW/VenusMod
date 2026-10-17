package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

/** 通常のブロック配線からリレー/ACLを検査。GUIやJEIの目視検証とは別。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkServicesGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,BlockPos pos){
        var p=h.absolutePos(pos);h.getLevel().setBlock(p,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(p);
    }
    private static void cable(GameTestHelper h,BlockPos pos){
        var p=h.absolutePos(pos);var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();
        h.getLevel().setBlock(p,Block.updateFromNeighbourShapes(state,h.getLevel(),p),3);
    }
    private static void configure(GameTestHelper h,NetworkWorld world,NetworkDeviceEntity device,String command){
        var reply=NetworkServiceCommands.run(world.simulator(),device.id(),command);
        h.assertTrue(reply.changed(),"設定成功: "+command+" / "+reply.lines());
        device.adoptProfile(world.simulator().nodes().get(device.id()));device.refreshState();
    }
    @GameTest(batch="network_services",template="test/wide_empty",timeoutTicks=200)
    public static void actualWiringRelayAclAndPackedReservation(GameTestHelper h){
        var client=device(h,DeviceKind.PC,new BlockPos(1,2,2));
        var router=device(h,DeviceKind.ROUTER,new BlockPos(4,2,2));
        var server=device(h,DeviceKind.SERVER,new BlockPos(4,2,5));
        router.profile().dhcp=false;router.profile().ports[3].address="10.71.1.1";router.profile().ports[2].address="10.71.2.1";
        server.profile().address="10.71.2.10";server.profile().gateway="10.71.2.1";server.profile().dhcp=true;
        for(var pos:java.util.List.of(new BlockPos(2,2,2),new BlockPos(3,2,2),new BlockPos(4,2,3),new BlockPos(4,2,4)))cable(h,pos);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"通常イベントで配線解析完了: "+world.diagnostic());
            configure(h,world,router,"dhcp-relay 3 10.71.2.10");
            configure(h,world,server,"dhcp-pool add 10.71.1.0/24 10.71.1.100 10.71.1.110 10.71.1.1 10.71.2.10");
            var sim=world.simulator();var lease=sim.dhcp(client.id());
            h.assertTrue(lease.success(),"実配線のDHCP relay: "+lease.message());
            h.assertTrue(client.profile().address.equals("10.71.1.100"),"giaddrでLAN-Aを選択");
            for(String id:sim.drainChangedProfiles()){var changed=world.device(id);if(changed!=null)changed.setChanged();}
            configure(h,world,router,"acl add 3 in 10 deny icmp any any");
            configure(h,world,router,"acl add 3 in 20 permit ip any any");
            h.assertTrue(!sim.ping(client.id(),server.profile().address).success(),"ICMP拒否");
            h.assertTrue(sim.request(client.id(),server.profile().address,"tcp",80).success(),"TCP80は許可");
            ItemStack packed=server.savedItem();
            var restored=device(h,DeviceKind.SERVER,new BlockPos(7,2,5));restored.restoreItem(packed);
            h.assertTrue(restored.profile().services.pools().size()==1,"機器アイテムでプールを引継ぎ");
            h.assertTrue(restored.profile().services.bindings().size()==1,"機器アイテムで配布予約を引継ぎ");
            var rules=NetworkProfile.decode(DeviceKind.ROUTER,router.profile().encode());
            h.assertTrue(rules.services.policy.rules().size()==2&&rules.services.relay(3).equals("10.71.2.10"),"ACLとrelay設定を保存");
        }).thenSucceed();
    }
    @GameTest(batch="network_services",template="test/empty")
    public static void legacyProfileMigration(GameTestHelper h){
        var p=NetworkProfile.decode(DeviceKind.ROUTER,"AAAAAQAJ5pen6Kit5a6aAAAAAAAYAAAAAAEBAAEAAAAAAABkAQAAAAAACgALMTkyLjE2OC4xLjEAAAAYAAAAAAAAAAABAAAAAAABAAsxOTIuMTY4LjIuMQAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAAA");
        h.assertTrue(p.name.equals("旧設定")&&p.failed&&p.ports[0].vlan==10,"旧バージョンの設定・故障を維持");
        h.assertTrue(p.services.policy.rules().isEmpty(),"旧機器にACLを勝手に適用しない");h.succeed();
    }
    @GameTest(batch="network_services",template="test/empty")
    public static void guideIconsAreRegisteredItems(GameTestHelper h){
        for(var page:NetworkGuide.pages())for(var node:page.nodes()){
            var item=NetworkContent.NETWORK_ITEMS.get(node.item());
            h.assertTrue(item!=null&&!new ItemStack(item.get()).isEmpty(),"ガイドの機器アイコンが実在: "+node.item());
        }
        int steps=NetworkGuide.pages().stream().mapToInt(page->page.steps().size()).sum();
        h.assertTrue(NetworkGuide.recipes().size()==steps,"各ガイド手順を漏れなくJEIへ登録");
        h.assertTrue(NetworkGuide.pages().stream().map(NetworkGuide.Page::id).collect(java.util.stream.Collectors.toSet()).containsAll(java.util.List.of("basic","vlan","routing","optical","wan","upgrades","redundancy","acl","dhcp_relay","services","ospf","nat","vrrp","stp")),"既存ページとOSPF/NAT/VRRP/STPを維持");
        h.assertTrue(NetworkGuide.pages().stream().anyMatch(page->page.id().equals("ospf")&&page.steps().size()==4),"OSPFの設定・隣接・迂回を4手順で案内");
        h.succeed();
    }
}
