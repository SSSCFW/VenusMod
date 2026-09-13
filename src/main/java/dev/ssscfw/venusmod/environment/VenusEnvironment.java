package dev.ssscfw.venusmod.environment;

import dev.ssscfw.venusmod.registry.VenusPhase2;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 曝露値はプレイヤーNBT、表示は本人だけに送るボスバー。クライアントから値は受信しない。 */
public final class VenusEnvironment {
    private static final String KEY = "venusmod_environment";
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private VenusEnvironment() {}
    public static int suitPieces(net.minecraft.world.entity.LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR) if (entity.getItemBySlot(slot).is(VenusPhase2.ENVIRONMENT_SUIT)) count++;
        return count;
    }
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 20 != 0) return;
        CompoundTag root = player.getPersistentData();
        CompoundTag data = root.getCompound(KEY);
        boolean onVenus = player.level().dimension().equals(VenusDimensionContent.VENUS);
        if (onVenus && !data.getBoolean("InVenus")) data.putInt("Grace", VenusEnvironmentConfig.GRACE.get());
        data.putBoolean("InVenus", onVenus);
        boolean active = onVenus && VenusEnvironmentConfig.ENABLED.get() && player.isAlive()
                && !player.isCreative() && !player.isSpectator() && player.level().getDifficulty() != Difficulty.PEACEFUL;
        int pieces = suitPieces(player);
        ItemStack offhand = player.getOffhandItem();
        boolean support = active && pieces < 4 && offhand.is(VenusPhase2.LIFE_SUPPORT.get());
        int gain = active ? ExposureRules.gain(pieces, support, player.hasEffect(MobEffects.FIRE_RESISTANCE),
                100 * VenusEnvironmentConfig.HEAT.get(), 100 * VenusEnvironmentConfig.PRESSURE.get(),
                100 * VenusEnvironmentConfig.CORROSION.get()) : 0;
        ExposureRules.Step step = ExposureRules.step(data.getInt("Exposure"), active ? data.getInt("Grace") : 0,
                gain, 100 * VenusEnvironmentConfig.RECOVERY.get());
        data.putInt("Exposure", step.exposure());
        // クリエイティブ切替やリログで猶予を補充しない。
        if (active) data.putInt("Grace", step.graceSeconds());
        root.put(KEY, data);
        if (!active) { hide(player.getUUID()); return; }
        if (support && step.graceSeconds() == 0) offhand.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        if (step.harmful() && player.tickCount % 40 == 0)
            player.hurt(player.damageSources().magic(), VenusEnvironmentConfig.DAMAGE.get().floatValue());
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(
                Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS));
        bar.addPlayer(player);
        Component status = step.graceSeconds() > 0 ? Component.translatable("hud.venusmod.grace", step.graceSeconds())
                : support ? Component.translatable("hud.venusmod.life_support", offhand.getMaxDamage() - offhand.getDamageValue())
                : Component.translatable("hud.venusmod.suit", pieces);
        bar.setName(Component.translatable("hud.venusmod.exposure", step.exposure() / 100, status));
        bar.setProgress(step.exposure() / (float) ExposureRules.MAX);
        bar.setColor(step.exposure() >= 8000 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
    }
    private static void hide(UUID id) {
        ServerBossEvent bar = BARS.remove(id);
        if (bar != null) bar.removeAllPlayers();
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { hide(event.getEntity().getUUID()); }
    public static void stopped(ServerStoppedEvent event) { BARS.values().forEach(ServerBossEvent::removeAllPlayers); BARS.clear(); }
}
