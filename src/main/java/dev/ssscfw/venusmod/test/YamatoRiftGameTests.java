package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRifts;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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


    @GameTest(template = "test/wide_empty")
    public static void fallingThroughHorizontalRiftsPreservesMomentumOnRepeatedTrips(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var data = new YamatoRifts();
        Vec3 lower = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        Vec3 upper = lower.add(0,5,0);
        var pair = data.openPair(UUID.randomUUID(),"fall loop",
                YamatoRifts.Endpoint.at(level,lower,0,90,1),
                YamatoRifts.Endpoint.at(level,upper,0,90,1),3,2,1,server);
        h.assertTrue(pair!=null,"Horizontal portals could not be created above each other");
        Pig pig=h.spawn(EntityType.PIG,new BlockPos(2,3,2));
        pig.setNoAi(true); pig.setNoGravity(true); pig.moveTo(lower.x,lower.y,lower.z,0,0);
        Vec3 falling=new Vec3(.17,-3.75,-.11);
        pig.setDeltaMovement(falling);
        data.tick(server,level.getGameTime()+7);
        h.assertTrue(pig.position().distanceToSqr(upper)<.01,"Falling entity did not arrive at upper portal");
        h.assertTrue(pig.getDeltaMovement().distanceToSqr(falling)<1.0E-12,"First teleport erased falling or lateral momentum");
        // 到着面から一度離れ、落下して下側へ戻る動きを模擬する。
        pig.moveTo(upper.x,upper.y-2,upper.z,0,0);
        data.tick(server,level.getGameTime()+8);
        pig.moveTo(lower.x,lower.y,lower.z,0,0);
        data.tick(server,level.getGameTime()+9);
        h.assertTrue(pig.position().distanceToSqr(upper)<.01,"Looping fall was blocked after leaving arrival portal");
        h.assertTrue(pig.getDeltaMovement().distanceToSqr(falling)<1.0E-12,"Second teleport reset accumulated velocity");
        data.releaseTickets(server);h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void itemAndExperiencePortalsPreserveTheirOwnVelocity(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var data=new YamatoRifts();
        Vec3 lower=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        Vec3 upper=lower.add(6,4,0);
        var pair=data.openPair(UUID.randomUUID(),"drops",
                YamatoRifts.Endpoint.at(level,lower,0,90,1),
                YamatoRifts.Endpoint.at(level,upper,0,90,1),6,2,1,server);
        h.assertTrue(pair!=null,"Item and experience portal could not be created");
        ItemEntity item=new ItemEntity(level,lower.x,lower.y,lower.z,new ItemStack(Items.STONE));
        ExperienceOrb orb=new ExperienceOrb(level,lower.x,lower.y,lower.z,7);
        item.setNoGravity(true);orb.setNoGravity(true);
        Vec3 itemVelocity=new Vec3(.24,-1.8,.09),orbVelocity=new Vec3(-.13,-2.4,-.06);
        item.setDeltaMovement(itemVelocity);orb.setDeltaMovement(orbVelocity);
        level.addFreshEntity(item);level.addFreshEntity(orb);
        data.tick(server,level.getGameTime()+7);
        h.assertTrue(item.position().distanceToSqr(upper)<.01,"Item did not pass through the drop-only portal");
        h.assertTrue(orb.position().distanceToSqr(upper)<.01,"Experience did not pass through the drop-only portal");
        h.assertTrue(item.getDeltaMovement().distanceToSqr(itemVelocity)<1.0E-12,"Item lost momentum");
        h.assertTrue(orb.getDeltaMovement().distanceToSqr(orbVelocity)<1.0E-12,"Experience lost momentum");
        data.releaseTickets(server);h.succeed();
    }

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
        Vec3 boatVelocity=new Vec3(.21,-.75,.08);
        boat.setDeltaMovement(boatVelocity);
        boat.positionRider(first);boat.positionRider(second);
        BlockPos obstruction=BlockPos.containing(b).offset(1,1,0);
        level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        data.tick(server,level.getGameTime()+7);
        h.assertTrue(boat.position().distanceTo(a)<.1,"Rotated rear passenger would intersect the wall");
        level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        data.tick(server,level.getGameTime()+8);
        h.assertTrue(boat.position().distanceTo(b)<.1 && first.getVehicle()==boat && second.getVehicle()==boat,"Allowed mounted group did not transfer intact");
        h.assertTrue(boat.getDeltaMovement().distanceToSqr(boatVelocity)<1.0E-12,
                "Boat velocity was reset while carrying passengers");
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
        ChunkPos destinationChunk=new ChunkPos(origin);
        // 保存先は訪問済みの場所。高速なGameTestの制限時間を未生成の周辺地形の生成に使わない。
        // FULL読込だけを先に済ませ、Entity tickingへの昇格は実際の裂け目のチケットで検証する。
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++)
            target.getChunk(destinationChunk.x+dx,destinationChunk.z+dz);
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-2,-1,-2),origin.offset(2,4,2)))
            target.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        var pair=data.openPair(UUID.randomUUID(),"dimension",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(target,b,0),3,2,1,server);
        h.assertTrue(pair!=null,"Cross-dimension pair");
        // 新規Netherチャンクのブロック読込とEntityの可視化は別段階。転送前に追跡可能になるまで待つ。
        h.startSequence().thenWaitUntil(() -> {
            data.tick(server,level.getGameTime());
            var chunk=target.getChunkSource().getChunkNow(destinationChunk.x,destinationChunk.z);
            h.assertTrue(target.isPositionEntityTicking(origin),"Destination chunk not entity-ticking yet: full="
                    +(chunk==null?"none":chunk.getFullStatus())
                    +", entitiesLoaded="+target.areEntitiesLoaded(destinationChunk.toLong())
                    +", entityVisibility="+target.isNaturalSpawningAllowed(origin)
                    +", blockTicking="+target.getChunkSource().isPositionTicking(destinationChunk.toLong()));
        }).thenExecute(() -> {
            Pig pig=h.spawn(EntityType.PIG,new BlockPos(2,3,2));pig.setNoAi(true);pig.setNoGravity(true);pig.moveTo(a.x,a.y,a.z,0,0);
            Vec3 incomingVelocity=new Vec3(.23,-2.65,.07);
            pig.setDeltaMovement(incomingVelocity);
            UUID id=pig.getUUID();
            h.assertTrue(level.getEntity(id)==pig,"Source fixture must track the test entity");
            data.tick(server,level.getGameTime()+7);
            var arrived=target.getEntity(id);
            h.assertTrue(arrived!=null && arrived.position().distanceTo(b)<.1,"Did not arrive at exact counterpart in Nether with same UUID");
            h.assertTrue(arrived.getDeltaMovement().distanceToSqr(incomingVelocity)<1.0E-12,
                    "Cross-dimensional teleport lost incoming velocity");
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
