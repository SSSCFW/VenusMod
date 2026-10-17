package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRifts;
import dev.ssscfw.venusmod.yamato.YamatoTravelData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 傾斜と倍率を実ワールドの配置・保存・Entity転送まで通して検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoOrientedRiftGameTests {
    private YamatoOrientedRiftGameTests() {}

    @GameTest(template="test/wide_empty")
    public static void lookingDownPlacesHorizontalRiftAboveFloor(GameTestHelper h) throws ReflectiveOperationException {
        for(BlockPos p:BlockPos.betweenClosed(new BlockPos(2,0,2),new BlockPos(6,0,6)))h.setBlock(p,Blocks.STONE);
        ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),
                new GameProfile(UUID.randomUUID(),"yamato-view"),ClientInformation.createDefault());
        Vec3 feet=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,1,4)));
        player.moveTo(feet.x,feet.y,feet.z,0,90);
        var method=YamatoRifts.class.getDeclaredMethod("inFront",ServerPlayer.class,float.class,float.class,double.class);
        method.setAccessible(true);
        YamatoRifts.Endpoint down=(YamatoRifts.Endpoint)method.invoke(null,player,0F,90F,1D);
        h.assertTrue(down!=null,"真下を向いたとき床内でなく直上へ開く");
        h.assertTrue(Math.abs(down.center().x-feet.x)<.001&&Math.abs(down.center().z-feet.z)<.001,"裂け目をプレイヤーの真下へ配置");
        h.assertTrue(down.center().y<player.getEyeY()&&down.feet().y>feet.y,"中心は視点より下、判定全体は床より上");
        h.assertTrue(Math.abs(down.bounds().getYsize()-.36)<.001,"水平な面の厚さを世界Yへ適用");
        h.assertTrue(down.pitch()==90&&down.size()==1,"生成後に固定する向きと倍率");
        YamatoRifts.Endpoint up=(YamatoRifts.Endpoint)method.invoke(null,player,0F,-90F,1D);
        h.assertTrue(up!=null&&up.center().y>player.getEyeY(),"真上にも水平面を配置");
        h.succeed();
    }

    @GameTest(template="test/wide_empty")
    public static void horizontalScaledRiftsRequireLeavingBeforeReturn(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var rifts=new YamatoRifts();UUID owner=UUID.randomUUID();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,3,4)));
        Vec3 b=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,3,10)));
        var source=YamatoRifts.Endpoint.at(level,a,25,90,2);
        var target=YamatoRifts.Endpoint.at(level,b,25,90,2);
        var pair=rifts.openPair(owner,"水平な2地点",source,target,3,2,1,server);
        h.assertTrue(pair!=null,"2倍の水平な両端を同時に生成");
        Pig pig=h.spawn(EntityType.PIG,new BlockPos(4,3,4));pig.setNoAi(true);pig.setNoGravity(true);pig.moveTo(a.x,a.y,a.z,0,0);
        rifts.tick(server,level.getGameTime()+7);
        h.assertTrue(pig.position().distanceTo(b)<.01,"水平の隙間に触れて反対端の足位置へ移動");
        rifts.tick(server,level.getGameTime()+1000);
        h.assertTrue(pig.position().distanceTo(b)<.01,"水平な到着端の中で待っても戻らない");
        pig.moveTo(b.x,b.y+2,b.z,0,0);rifts.tick(server,level.getGameTime()+1001);
        pig.moveTo(b.x,b.y,b.z,0,0);rifts.tick(server,level.getGameTime()+1002);
        h.assertTrue(pig.position().distanceTo(a)<.01,"水平面を縦方向へ抜けて再接触したら戻る");
        var active=rifts.active(owner,server).getCompound(0);
        h.assertTrue(active.getDouble("size")==2&&active.getFloat("pitch")==90,"開放中一覧へ倍率と角度を伝える");
        CompoundTag saved=rifts.save(new CompoundTag(),level.registryAccess());
        rifts.releaseTickets(server);
        var restored=YamatoRifts.load(saved,level.registryAccess());
        h.assertTrue(restored.active(owner,server).getCompound(0).getDouble("size")==2,"再起動で倍率を維持");
        restored.tick(server,level.getGameTime()+1003);
        h.assertTrue(pig.position().distanceTo(a)<.01,"再起動で水平面の到着ロックを維持");
        restored.releaseTickets(server);h.succeed();
    }

    @GameTest(template="test/wide_empty")
    public static void historyOnlyRecordsSuccessfulSourceAndRetainsOldEndpointDefaults(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();UUID owner=UUID.randomUUID();var rifts=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,3,4))),b=a.add(6,0,0);
        var pair=rifts.openPair(owner,"履歴の出発端",YamatoRifts.Endpoint.at(level,a,30,25,.5),
                YamatoRifts.Endpoint.at(level,b,30,25,.5),3,2,1,server);
        h.assertTrue(pair!=null,"小さい斜めの端を生成");
        var data=YamatoTravelData.get(server);
        var history=data.snapshot(owner).getList("history",Tag.TAG_COMPOUND);
        h.assertTrue(history.size()==1,"成功した1回の開通を記録");
        CompoundTag entry=history.getCompound(0);
        h.assertTrue(entry.getDouble("x")==a.x&&entry.getDouble("y")==a.y&&entry.getDouble("z")==a.z,"プレイヤーや終点の位置に取り違えず出発端を記録");
        h.assertTrue(data.destination(owner,entry.getUUID("id"))!=null,"記録した履歴を移動先として解決");
        level.setBlock(BlockPos.containing(b),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(rifts.openPair(owner,"塞がった場所",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,b,0),3,2,1,server)==null,"塞がった移動先は生成しない");
        h.assertTrue(data.snapshot(owner).getList("history",Tag.TAG_COMPOUND).size()==1,"失敗を履歴へ加えない");
        CompoundTag saved=rifts.save(new CompoundTag(),level.registryAccess());
        CompoundTag savedPair=saved.getList("pairs",Tag.TAG_COMPOUND).getCompound(0);
        for(String side:new String[]{"source","target"}) {
            savedPair.getCompound(side).remove("pitch");savedPair.getCompound(side).remove("size");
        }
        var restored=YamatoRifts.load(saved,level.registryAccess()).active(owner,server).getCompound(0);
        h.assertTrue(restored.getFloat("pitch")==0&&restored.getDouble("size")==1,"旧端データは縦向き1倍として読む");
        savedPair.getCompound("source").putDouble("size",Double.NaN);
        h.assertTrue(YamatoRifts.load(saved,level.registryAccess()).active(owner,server).isEmpty(),"不正な倍率の保存データを描画・転送しない");
        rifts.releaseTickets(server);h.succeed();
    }

    @GameTest(template="test/wide_empty")
    public static void itemOnlyRiftFiltersActualEntities(GameTestHelper h) { filteredEntities(h,4); }

    @GameTest(template="test/wide_empty")
    public static void exactDepthContactSurvivesEntityBroadPhase(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var rifts=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,3,4))),b=a.add(6,0,0);
        var source=YamatoRifts.Endpoint.at(level,a,0);
        h.assertTrue(rifts.openPair(UUID.randomUUID(),"接触境界",source,YamatoRifts.Endpoint.at(level,b,0),4,2,1,server)!=null,"境界試験の対");
        ItemEntity item=new ItemEntity(level,a.x,a.y+1,a.z,new ItemStack(Items.GOLD_INGOT));
        item.setPos(a.x,a.y+1,source.bounds().maxZ+item.getBbWidth()/2.0);
        item.setNoGravity(true);level.addFreshEntity(item);
        h.assertTrue(item.getBoundingBox().minZ==source.bounds().maxZ,"広域AABBの深さ面に正確に触れる個体を用意");
        h.assertTrue(YamatoRifts.touching(item.getBoundingBox(),source,1),"最終の曲線判定は境界接触を含む");
        rifts.tick(server,level.getGameTime()+7);
        h.assertTrue(item.position().distanceTo(b)<.01,"広域候補検索で境界の対象を取りこぼさない");
        rifts.releaseTickets(server);h.succeed();
    }

    @GameTest(template="test/wide_empty")
    public static void experienceOnlyRiftFiltersActualEntities(GameTestHelper h) { filteredEntities(h,5); }

    @GameTest(template="test/wide_empty")
    public static void itemAndExperienceRiftFiltersActualEntities(GameTestHelper h) { filteredEntities(h,6); }

    private static void filteredEntities(GameTestHelper h,int access) {
        var level=h.getLevel();var server=level.getServer();var rifts=new YamatoRifts();
        Vec3 a=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,3,4))),b=a.add(6,0,0);
        h.assertTrue(rifts.openPair(UUID.randomUUID(),"対象別",YamatoRifts.Endpoint.at(level,a,0),
                YamatoRifts.Endpoint.at(level,b,0),access,2,1,server)!=null,"新しい対象条件を受理");
        ItemEntity item=new ItemEntity(level,a.x,a.y,a.z,new ItemStack(Items.GOLD_INGOT));item.setNoGravity(true);level.addFreshEntity(item);
        ExperienceOrb xp=new ExperienceOrb(level,a.x,a.y,a.z,7);xp.setNoGravity(true);level.addFreshEntity(xp);
        Pig pig=h.spawn(EntityType.PIG,new BlockPos(4,3,4));pig.setNoAi(true);pig.setNoGravity(true);pig.moveTo(a.x,a.y,a.z,0,0);
        rifts.tick(server,level.getGameTime()+7);
        h.assertTrue(item.position().distanceTo(access==5?a:b)<.01,"アイテムの転送条件 "+access);
        h.assertTrue(xp.position().distanceTo(access==4?a:b)<.01,"経験値の転送条件 "+access);
        h.assertTrue(pig.position().distanceTo(a)<.01,"対象外の友好MOBを転送しない");
        rifts.releaseTickets(server);h.succeed();
    }
}
