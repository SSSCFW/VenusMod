package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRiftGeometry;
import dev.ssscfw.venusmod.yamato.YamatoRifts;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実際の離脱イベントを通し、消滅と一時的な未読込で保存ロックを区別する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoRiftLifecycleGameTests {
    private YamatoRiftLifecycleGameTests() {}

    @GameTest(template = "test/empty")
    public static void destroyedArrivalsAreRemovedFromSavedLocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        YamatoRifts data = YamatoRifts.get(level.getServer());
        UUID owner = UUID.randomUUID();
        Vec3 source = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        Vec3 target = source.add(6, 0, 0);
        try {
            open(helper, data, owner, source, target);
            ItemEntity destroyed = item(level, source);
            ItemEntity survivor = item(level, source);
            data.tick(level.getServer(), level.getGameTime() + YamatoRiftGeometry.FADE_TICKS + 1);
            helper.assertTrue(destroyed.position().distanceTo(target) < .01, "Item did not arrive through the portal");
            helper.assertTrue(savedLock(data, level, destroyed.getUUID()), "Transferred item had no saved arrival lock");
            helper.assertTrue(savedLock(data, level, survivor.getUUID()), "Second item had no saved arrival lock");
            data.setDirty(false);
            destroyed.discard();
            helper.assertTrue(!savedLock(data, level, destroyed.getUUID()), "Discarded item left a permanent arrival lock");
            helper.assertTrue(data.isDirty(), "Destroy cleanup did not mark saved arrival locks dirty");
            helper.assertTrue(savedLock(data, level, survivor.getUUID()), "Destroy cleanup erased another item's arrival lock");
            survivor.discard();
        } finally {
            close(data, owner, level);
        }
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void temporaryRemovalPreservesArrivalLocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        YamatoRifts data = YamatoRifts.get(level.getServer());
        UUID owner = UUID.randomUUID();
        Vec3 source = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        Vec3 target = source.add(6, 0, 0);
        try {
            open(helper, data, owner, source, target);
            for (Entity.RemovalReason reason : new Entity.RemovalReason[]{Entity.RemovalReason.UNLOADED_TO_CHUNK,
                    Entity.RemovalReason.UNLOADED_WITH_PLAYER, Entity.RemovalReason.CHANGED_DIMENSION}) {
                ItemEntity arrival = item(level, source);
                data.tick(level.getServer(), level.getGameTime() + YamatoRiftGeometry.FADE_TICKS + 1);
                helper.assertTrue(arrival.position().distanceTo(target) < .01, "Item did not arrive before " + reason);
                arrival.remove(reason);
                data.tick(level.getServer(), level.getGameTime() + YamatoRiftGeometry.FADE_TICKS + 2);
                helper.assertTrue(savedLock(data, level, arrival.getUUID()), "Temporary removal erased arrival lock: " + reason);
            }
        } finally {
            close(data, owner, level);
        }
        helper.succeed();
    }

    private static void open(GameTestHelper helper, YamatoRifts data, UUID owner, Vec3 source, Vec3 target) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(data.openPair(owner, "lifecycle", YamatoRifts.Endpoint.at(level, source, 0),
                YamatoRifts.Endpoint.at(level, target, 0), 3, 2, 1, level.getServer()) != null,
                "Could not create lifecycle test portal pair");
    }

    private static ItemEntity item(ServerLevel level, Vec3 position) {
        ItemEntity item = new ItemEntity(level, position.x, position.y + 1.0, position.z, new ItemStack(Items.STONE));
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        return item;
    }

    private static boolean savedLock(YamatoRifts data, ServerLevel level, UUID entity) {
        ListTag pairs = data.save(new CompoundTag(), level.registryAccess()).getList("pairs", Tag.TAG_COMPOUND);
        for (int pairIndex = 0; pairIndex < pairs.size(); pairIndex++) {
            CompoundTag pair = pairs.getCompound(pairIndex);
            for (String side : new String[]{"sourceLocks", "targetLocks"}) {
                ListTag locks = pair.getList(side, Tag.TAG_COMPOUND);
                for (int lockIndex = 0; lockIndex < locks.size(); lockIndex++) {
                    CompoundTag lock = locks.getCompound(lockIndex);
                    if (lock.hasUUID("entity") && lock.getUUID("entity").equals(entity)) return true;
                }
            }
        }
        return false;
    }

    private static void close(YamatoRifts data, UUID owner, ServerLevel level) {
        data.closeAll(owner, level.getServer());
        data.tick(level.getServer(), level.getGameTime() + YamatoRiftGeometry.FADE_TICKS + 1);
    }
}
