package dev.ssscfw.venusmod.upgrade;

import dev.ssscfw.venusmod.compat.SlashBladeCompat;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** SlashBladeをハード依存にせず集中ランクの追加分だけを加える橋渡し。 */
public final class BladeUpgradeRankCompat {
    private static final ResourceLocation CONCENTRATION_ATTACHMENT =
            ResourceLocation.fromNamespaceAndPath(SlashBladeCompat.MOD_ID, "concentration");
    private static boolean lookupDone;
    private static Method bladeStateOf;
    private static Method resolveCurrentCombo;
    private static Method getUnitCapacity;
    private static Method getModifierDamage;
    private static Method getModifierCombo;
    private static Method addRankPoint;

    private BladeUpgradeRankCompat() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addBonus(LivingEntity user, DamageSource source, double multiplier) {
        if (!(multiplier > 0.0D) || !SlashBladeEnchantmentCompat.isBlade(user.getMainHandItem()) || !resolve()) return;
        AttachmentType<?> attachment = NeoForgeRegistries.ATTACHMENT_TYPES.get(CONCENTRATION_ATTACHMENT);
        if (attachment == null) return;
        try {
            Object rank = user.getData((AttachmentType) attachment);
            long unit = ((Number) getUnitCapacity.invoke(rank)).longValue();
            float modifier = modifier(rank, user, source);
            long normalGain = (long) (modifier * unit);
            long bonus = (long) (normalGain * multiplier);
            if (bonus > 0L) addRankPoint.invoke(rank, user, bonus);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // SlashBlade API変更時もVenusMod全体はロード可能にする。
        }
    }

    private static float modifier(Object rank, LivingEntity user, DamageSource source) throws ReflectiveOperationException {
        Object stateOptional = bladeStateOf.invoke(null, user.getMainHandItem());
        if (stateOptional instanceof Optional<?> optional && optional.isPresent()) {
            Object combo = resolveCurrentCombo.invoke(optional.get(), user);
            if (combo instanceof ResourceLocation location) {
                return ((Number) getModifierCombo.invoke(rank, location)).floatValue();
            }
        }
        return ((Number) getModifierDamage.invoke(rank, source)).floatValue();
    }

    private static boolean resolve() {
        if (lookupDone) return bladeStateOf != null;
        lookupDone = true;
        try {
            Class<?> bladeStateAccess = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> bladeState = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            Class<?> rank = Class.forName("mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank");
            bladeStateOf = bladeStateAccess.getMethod("of", net.minecraft.world.item.ItemStack.class);
            resolveCurrentCombo = bladeState.getMethod("resolvCurrentComboState", LivingEntity.class);
            getUnitCapacity = rank.getMethod("getUnitCapacity");
            getModifierDamage = rank.getMethod("getRankPointModifier", DamageSource.class);
            getModifierCombo = rank.getMethod("getRankPointModifier", ResourceLocation.class);
            addRankPoint = rank.getMethod("addRankPoint", LivingEntity.class, long.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            bladeStateOf = null;
            return false;
        }
    }
}
