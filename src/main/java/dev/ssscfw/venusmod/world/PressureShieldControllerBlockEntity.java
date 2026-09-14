package dev.ssscfw.venusmod.world;

import dev.ssscfw.venusmod.entity.BossCombatRules;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

public final class PressureShieldControllerBlockEntity extends BlockEntity {
    private int charge;
    private boolean unlocked;

    public PressureShieldControllerBlockEntity(BlockPos pos, BlockState state) {
        super(VenusDimensionContent.SHIELD_CONTROLLER_ENTITY.get(), pos, state);
    }

    public int charge() { return charge; }
    public boolean isUnlocked() { return unlocked; }

    public static void tick(Level level, BlockPos pos, BlockState state, PressureShieldControllerBlockEntity controller) {
        if (!(level instanceof ServerLevel server) || controller.unlocked) return;
        boolean create = ModList.get().isLoaded("create");
        float rpm = create ? controller.maxAdjacentCreateSpeed(server) : 0.0F;
        int next = BossCombatRules.controllerCharge(controller.charge, rpm, create, server.hasNeighborSignal(pos));
        if (next != controller.charge) {
            controller.charge = next;
            controller.setChanged();
        }
        if (controller.charge >= 100) {
            controller.unlocked = true;
            controller.charge = 100;
            controller.setChanged();
            server.setBlock(pos, state.setValue(PressureShieldControllerBlock.POWERED, true), 3);
            server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 0.75F);
        }
    }

    private float maxAdjacentCreateSpeed(ServerLevel level) {
        float best = 0.0F;
        for (Direction direction : Direction.values()) {
            BlockEntity adjacent = level.getBlockEntity(worldPosition.relative(direction));
            if (adjacent == null || !adjacent.getClass().getName().startsWith("com.simibubi.create.")) continue;
            try {
                Method method = adjacent.getClass().getMethod("getSpeed");
                Object value = method.invoke(adjacent);
                if (value instanceof Number number) best = Math.max(best, Math.abs(number.floatValue()));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // CreateのAPI変更時は単にその隣接ブロックを回転源として数えない。
            }
        }
        return best;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Charge", charge);
        tag.putBoolean("Unlocked", unlocked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = Math.clamp(tag.getInt("Charge"), 0, 100);
        unlocked = tag.getBoolean("Unlocked");
    }
}
