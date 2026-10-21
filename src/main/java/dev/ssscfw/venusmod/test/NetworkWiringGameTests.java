package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 手作りの論理Linkを注入せず、実ブロックと通常のロード・tickイベントで通信を検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkWiringGameTests {
    private NetworkWiringGameTests() {}

    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,BlockPos local) {
        BlockPos pos=h.absolutePos(local);
        h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void cable(GameTestHelper h,CableGrade grade,BlockPos local) {cable(h,grade,null,local);}
    private static void cable(GameTestHelper h,CableGrade grade,DyeColor color,BlockPos local) {
        BlockPos pos=h.absolutePos(local);
        var block=color==null?NetworkContent.CABLES.get(grade).get():NetworkContent.DYED_CABLES.get(grade).get(color).get();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(block.defaultBlockState(),h.getLevel(),pos),3);
    }
    // 配線解析はtick予算で分割され、同じ世界の別試験も配線を更新する。
    // 固定tick待ちだけに頼らず、変更処理とは別のthenWaitUntilで完了を待つ。
    private static void ready(GameTestHelper h,NetworkWorld world) {
        h.assertTrue(world.ready(),"配線解析が終了している: "+world.diagnostic());
    }

    @GameTest(template="test/wide_empty",timeoutTicks=200)
    public static void physicalCableBranchAndRecovery(GameTestHelper h) {
        var a=device(h,DeviceKind.PC,new BlockPos(1,2,1));
        device(h,DeviceKind.L2_SWITCH,new BlockPos(3,2,1));
        var b=device(h,DeviceKind.PC,new BlockPos(5,2,1));
        a.profile().address="10.44.1.10";b.profile().address="10.44.1.20";
        cable(h,CableGrade.CAT5E,new BlockPos(2,2,1));
        cable(h,CableGrade.CAT5E,new BlockPos(4,2,1));
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence()
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(world.simulator().nodes().containsKey(a.id()),"onLoadで機器を自動登録");
                    var result=world.simulator().ping(a.id(),b.profile().address);
                    h.assertTrue(result.success()&&result.bps()==1_000_000_000L,"実配線経由の1Gbps往復通信");
                    h.assertTrue(!world.simulator().arpTable(a.id()).isEmpty(),"実配線のARP学習");
                    var state=h.getLevel().getBlockState(h.absolutePos(new BlockPos(2,2,1)));
                    h.assertTrue(state.getValue(NetworkCableBlock.CONNECTIONS[1])&&state.getValue(NetworkCableBlock.CONNECTIONS[3]),"東西への細い接続腕");
                    cable(h,CableGrade.CAT5E,new BlockPos(2,2,2));
                })
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(!world.simulator().ping(a.id(),b.profile().address).success(),"T字分岐は通信を成立させない");
                    h.getLevel().removeBlock(h.absolutePos(new BlockPos(2,2,2)),false);
                })
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(world.simulator().ping(a.id(),b.profile().address).success(),"分岐撤去後にリンクが復旧");
                    h.getLevel().removeBlock(h.absolutePos(new BlockPos(4,2,1)),false);
                })
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(!world.simulator().ping(a.id(),b.profile().address).success(),"ケーブル本体の切断を反映");
                })
                .thenSucceed();
    }

    @GameTest(template="test/wide_empty",timeoutTicks=200)
    public static void coloredCableIsolationAndReconnection(GameTestHelper h) {
        var a=device(h,DeviceKind.PC,new BlockPos(1,2,1));
        var b=device(h,DeviceKind.PC,new BlockPos(5,2,1));
        a.profile().address="10.63.1.10";b.profile().address="10.63.1.20";
        cable(h,CableGrade.CAT5E,DyeColor.BLUE,new BlockPos(2,2,1));
        cable(h,CableGrade.CAT5E,DyeColor.RED,new BlockPos(3,2,1));
        cable(h,CableGrade.CAT5E,DyeColor.BLUE,new BlockPos(4,2,1));
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence()
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(!world.simulator().ping(a.id(),b.profile().address).success(),"同規格でも色違いは通信しない");
                    h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(new BlockPos(2,2,1))).getValue(NetworkCableBlock.CONNECTIONS[1]),"異色ケーブルへ接続腕を伸ばさない");
                    cable(h,CableGrade.CAT5E,DyeColor.BLUE,new BlockPos(3,2,1));
                })
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(world.simulator().ping(a.id(),b.profile().address).success(),"青一色にすれば通信が復旧");
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(2,2,1))).getValue(NetworkCableBlock.CONNECTIONS[1]),"同色ケーブルは見た目も接続");
                    cable(h,CableGrade.CAT5E,new BlockPos(3,2,1));
                })
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(!world.simulator().ping(a.id(),b.profile().address).success(),"無染色と染色済みも不通");
                })
                .thenSucceed();
    }

    @GameTest(template="test/wide_empty",timeoutTicks=200)
    public static void opticalModulesAndSpeedNegotiation(GameTestHelper h) {
        var a=device(h,DeviceKind.PC,new BlockPos(1,2,4));
        var b=device(h,DeviceKind.PC,new BlockPos(3,2,4));
        a.profile().address="10.45.1.10";b.profile().address="10.45.1.20";
        cable(h,CableGrade.OM3,new BlockPos(2,2,4));
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence()
                .thenIdle(10)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    h.assertTrue(!world.simulator().ping(a.id(),b.profile().address).success(),"銅線標準ポートと光線は非互換");
                    a.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_10g").get()));
                    b.modules.setItem(3,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_10g").get()));
                    a.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_processor").get()));
                    b.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_processor").get()));
                    a.profile().ports[1].optical=true;b.profile().ports[3].optical=true;
                    a.profile().ports[1].speedLimitBps=10_000_000_000L;
                    a.refreshState();b.refreshState();
                })
                .thenIdle(5)
                .thenWaitUntil(()->ready(h,world))
                .thenExecute(()->{
                    ready(h,world);
                    var result=world.simulator().ping(a.id(),b.profile().address);
                    h.assertTrue(result.success()&&result.bps()==10_000_000_000L,"両端強化後は光10Gbpsで接続");
                    a.modules.removeItemNoUpdate(1);a.modules.setChanged();
                })
                .thenIdle(5)
                .thenExecute(()->{
                    h.assertTrue(!world.simulator().ping(a.id(),b.profile().address).success(),"対応モジュール抜去で停止");
                    h.assertTrue(a.profile().ports[1].speedLimitBps==10_000_000_000L,"固定速度の希望設定を保持");
                    a.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_10g").get()));
                })
                .thenIdle(5)
                .thenExecute(()->h.assertTrue(world.simulator().ping(a.id(),b.profile().address).success(),"モジュール再装着で復旧"))
                .thenSucceed();
    }

    @GameTest(template="test/empty")
    public static void deviceModelsNeverOccludeNeighbors(GameTestHelper h) {
        for(var kind:DeviceKind.values()) {
            var block=NetworkContent.DEVICES.get(kind).get();
            for(var state:block.getStateDefinition().getPossibleStates())
                h.assertTrue(state.getOcclusionShape(h.getLevel(),BlockPos.ZERO).isEmpty(),"機器の隣接面を隠さない: "+kind);
        }
        h.succeed();
    }
}
