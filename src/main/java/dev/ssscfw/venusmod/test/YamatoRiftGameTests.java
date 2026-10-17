package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRifts;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 保存・同tickの対・乗客・到着ロックを実際のサーバーEntityで検証する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoRiftGameTests {
    private YamatoRiftGameTests() {}

    @GameTest(template = "test/wide_empty", timeoutTicks = 100)
    public static void arrivalMustLeaveBeforeReturning(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer();
        var data = new YamatoRifts(); UUID owner = UUID.randomUUID();
        Vec3 a = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 3, 2)));
        Vec3 b = a.add(6, 0, 0);
        var pair = data.openPair(owner, "test", YamatoRifts.Endpoint.at(level, a, 0),
                YamatoRifts.Endpoint.at(level, b, 0), 3, 2, 1, server);
        h.assertTrue(pair != null, "Could not create portal pair");
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(2, 3, 2)); pig.setNoAi(true); pig.setNoGravity(true);
        pig.moveTo(a.x, a.y, a.z, 0, 0);
        data.tick(server, level.getGameTime() + 7);
        h.assertTrue(pig.position().distanceTo(b) < .1, "Entry did not transfer to exact exit");
        data.tick(server, level.getGameTime() + 1000);
        h.assertTrue(pig.position().distanceTo(b) < .1, "Standing inside exit caused a timed bounce");
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        var restored = YamatoRifts.load(saved, level.registryAccess());
        data.releaseTickets(server);
        restored.tick(server, level.getGameTime() + 1001);
        h.assertTrue(pig.position().distanceTo(b) < .1, "Save/reload lost arrival lock");
        pig.moveTo(b.x, b.y, b.z + 2, 0, 0);
        restored.tick(server, level.getGameTime() + 1002);
        pig.moveTo(b.x, b.y, b.z, 0, 0);
        restored.tick(server, level.getGameTime() + 1003);
        h.assertTrue(pig.position().distanceTo(a) < .1, "Leaving and re-entering must allow return");
        restored.releaseTickets(server); h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void pairLifetimeAndOwnerControlSurviveSave(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var data = new YamatoRifts();
        UUID owner = UUID.randomUUID(); Vec3 a = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        var pair = data.openPair(owner,"test",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,a.add(6,0,0),0),3,1,180,server);
        h.assertTrue(pair != null,"pair");
        h.assertTrue(data.active(owner,server).size()==1,"One pair, not two independent gates");
        h.assertTrue(data.active(UUID.randomUUID(),server).isEmpty(),"Other player can see owner's active management data");
        data.close(UUID.randomUUID(),pair.id(),server);
        h.assertTrue(data.active(owner,server).getCompound(0).getInt("remaining")==180,"Non-owner closed the gate");
        var copy=YamatoRifts.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        h.assertTrue(copy.active(owner,server).size()==1,"Active pair lost on restart");
        data.close(owner,pair.id(),server);
        data.tick(server,level.getGameTime()+7);
        h.assertTrue(data.active(owner,server).isEmpty(),"Manual close left an endpoint behind");
        data.releaseTickets(server);copy.releaseTickets(server);h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void friendlyModeDoesNotCarryHostilePassengers(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var data=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        var pair=data.openPair(UUID.randomUUID(),"test",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,a.add(6,0,0),0),2,2,1,server);
        h.assertTrue(pair!=null,"pair");
        Pig pig=h.spawn(EntityType.PIG,new BlockPos(2,3,2));pig.setNoAi(true);pig.setNoGravity(true);
        Zombie zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(2,3,2));zombie.setNoAi(true);zombie.setNoGravity(true);
        pig.moveTo(a.x,a.y,a.z,0,0);zombie.startRiding(pig,true);
        data.tick(server,level.getGameTime()+7);
        h.assertTrue(pig.position().distanceTo(a)<.1,"Vehicle dragged a forbidden passenger through");
        h.assertTrue(zombie.level()==level && zombie.distanceTo(pig)<3,"Forbidden hostile moved");
        zombie.stopRiding();zombie.moveTo(a.x,a.y,a.z+4,0,0);
        data.tick(server,level.getGameTime()+8);
        h.assertTrue(pig.position().distanceTo(a.add(6,0,0))<.1,"Friendly mob without hostile passenger could not pass");
        data.releaseTickets(server);h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void obstructedDestinationNeverMovesEntity(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var data=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2))),b=a.add(6,0,0);
        var pair=data.openPair(UUID.randomUUID(),"test",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,b,0),3,2,1,server);
        h.assertTrue(pair!=null,"pair");
        level.setBlock(BlockPos.containing(b),Blocks.STONE.defaultBlockState(),3);
        Pig pig=h.spawn(EntityType.PIG,new BlockPos(2,3,2));pig.setNoAi(true);pig.setNoGravity(true);
        pig.moveTo(a.x,a.y,a.z,0,0);data.tick(server,level.getGameTime()+7);
        h.assertTrue(pig.position().distanceTo(a)<.1,"Teleported into a newly placed solid block");
        data.releaseTickets(server);h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void openingRequiresRoomForEntirePortal(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var data=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2))),b=a.add(6,0,0);
        BlockPos ceiling=BlockPos.containing(b.add(0,2,0));
        level.setBlock(ceiling,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(data.openPair(UUID.randomUUID(),"low ceiling",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,b,0),3,2,1,server)==null,"Portal was created through a low ceiling");
        level.setBlock(ceiling,Blocks.AIR.defaultBlockState(),3);
        double edge=level.getWorldBorder().getMaxX();
        Vec3 outside=a.add(edge-.4-a.x,0,0);
        h.assertTrue(data.openPair(UUID.randomUUID(),"border",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,outside,0),3,2,1,server)==null,"Portal extended outside world border");
        data.releaseTickets(server);h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void rotatedBoatChecksAndPositionsBothPassengers(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var data=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2))),b=a.add(5.7,0,0);
        var pair=data.openPair(UUID.randomUUID(),"boat",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,b,90),3,2,1,server);
        h.assertTrue(pair!=null,"pair");
        Boat boat=h.spawn(EntityType.BOAT,new BlockPos(2,3,2));boat.setNoGravity(true);boat.moveTo(a.x,a.y,a.z,0,0);
        Zombie first=h.spawn(EntityType.ZOMBIE,new BlockPos(2,3,2));first.setNoAi(true);first.setNoGravity(true);
        Zombie second=h.spawn(EntityType.ZOMBIE,new BlockPos(2,3,2));second.setNoAi(true);second.setNoGravity(true);
        h.assertTrue(first.startRiding(boat,true)&&second.startRiding(boat,true),"Both riders mount");
        boat.positionRider(first);boat.positionRider(second);
        BlockPos obstruction=BlockPos.containing(b).offset(1,1,0);
        level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        data.tick(server,level.getGameTime()+7);
        h.assertTrue(boat.position().distanceTo(a)<.1,"Rotated rear passenger would intersect the wall");
        level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        data.tick(server,level.getGameTime()+8);
        h.assertTrue(boat.position().distanceTo(b)<.1 && first.getVehicle()==boat && second.getVehicle()==boat,"Allowed mounted group did not transfer intact");
        Vec3 expected=boat.getPassengerRidingPosition(second).subtract(second.getVehicleAttachmentPoint(boat));
        h.assertTrue(second.position().distanceTo(expected)<.001,"Arrival must immediately use final rotated seat position");
        h.assertTrue(level.noCollision(second,second.getBoundingBox()),"Passenger arrived inside blocks");
        data.tick(server,level.getGameTime()+1000);
        h.assertTrue(boat.position().distanceTo(b)<.1,"Mounted group bounced without leaving exit");
        data.releaseTickets(server);h.succeed();
    }

    @GameTest(template = "test/wide_empty", timeoutTicks = 200)
    public static void crossDimensionArrivalKeepsIdentityAndRequiresExit(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var target=server.getLevel(Level.NETHER);var data=new YamatoRifts();
        h.assertTrue(target!=null,"Nether dimension available");
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        Vec3 b=new Vec3(a.x,220,a.z);
        BlockPos origin=BlockPos.containing(b);
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-2,-1,-2),origin.offset(2,4,2)))
            target.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        var pair=data.openPair(UUID.randomUUID(),"dimension",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(target,b,0),3,2,1,server);
        h.assertTrue(pair!=null,"Cross-dimension pair");
        // 新規Netherチャンクのブロック読込とEntityの可視化は別段階。転送前に追跡可能になるまで待つ。
        h.startSequence().thenWaitUntil(() -> {
            data.tick(server,level.getGameTime());
            h.assertTrue(target.isPositionEntityTicking(BlockPos.containing(b)),"Destination chunk not entity-ticking yet");
        }).thenExecute(() -> {
            Pig pig=h.spawn(EntityType.PIG,new BlockPos(2,3,2));pig.setNoAi(true);pig.setNoGravity(true);pig.moveTo(a.x,a.y,a.z,0,0);
            UUID id=pig.getUUID();
            h.assertTrue(level.getEntity(id)==pig,"Source fixture must track the test entity");
            data.tick(server,level.getGameTime()+7);
            var arrived=target.getEntity(id);
            h.assertTrue(arrived!=null && arrived.position().distanceTo(b)<.1,"Did not arrive at exact counterpart in Nether with same UUID");
            h.assertTrue(level.getEntity(id)==null,"Source dimension kept a duplicate entity");
            data.tick(server,level.getGameTime()+1000);
            h.assertTrue(target.getEntity(id)==arrived,"Cross-dimension arrival immediately returned");
            arrived.moveTo(b.x,b.y,b.z+2,0,0);data.tick(server,level.getGameTime()+1001);
            arrived.moveTo(b.x,b.y,b.z,0,0);data.tick(server,level.getGameTime()+1002);
            var returned=level.getEntity(id);
            h.assertTrue(returned!=null && returned.position().distanceTo(a)<.1,"Exit then re-entry did not return across dimensions");
            h.assertTrue(target.getEntity(id)==null,"Destination kept a duplicate on return");
            data.releaseTickets(server);
        }).thenSucceed();
    }
}
