package dev.ssscfw.venusmod.event;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeData;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRankCompat;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeType;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 金星刀鍛錬機で付与した独立強化のランタイム効果。 */
public final class BladeUpgradeEvents {
    private BladeUpgradeEvents() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // 王の財宝は「放出した刀自身」のベースダメージ/エンチャントから計算する。
        // 着弾時にプレイヤーが手に持っている別の刀の金星強化を二重加算しない。
        if (event.getSource().is(KingsTreasure.DAMAGE_TYPE)) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        ItemStack blade = livingAttacker.getMainHandItem();
        if (!SlashBladeEnchantmentCompat.isBlade(blade)) return;

        Entity direct = event.getSource().getDirectEntity();
        float amount = event.getAmount();
        if (SlashBladeEnchantmentCompat.isSummonedSword(direct)) {
            int level = BladeUpgradeData.getLevel(blade, BladeUpgradeType.SUMMONED_SWORD);
            amount += BladeUpgradeRules.summonedSwordBonus(level);
        } else {
            int level = BladeUpgradeData.getLevel(blade, BladeUpgradeType.BLADE_POWER);
            amount += BladeUpgradeRules.bladePowerBonus(level);
        }

        if (event.getEntity().level().dimension().equals(VenusDimensionContent.VENUS)) {
            int level = BladeUpgradeData.getLevel(blade, BladeUpgradeType.VENUS_SLAYER);
            amount *= BladeUpgradeRules.venusDamageMultiplier(level);
        }
        event.setAmount(amount);
    }

    public static void onDamagePost(LivingDamageEvent.Post event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        ItemStack blade = livingAttacker.getMainHandItem();
        if (!SlashBladeEnchantmentCompat.isBlade(blade)) return;
        int level = BladeUpgradeData.getLevel(blade, BladeUpgradeType.CONCENTRATION);
        if (level <= 0) return;
        BladeUpgradeRankCompat.addBonus(
                livingAttacker,
                event.getSource(),
                BladeUpgradeRules.concentrationBonusMultiplier(level));
    }

    /**
     * SlashBladeのDAMAGE component増加を観測し、Lvごと10%ずつ（最大50%）耐久消費を返却する。
     * 小数分はWearCreditとして刀自身に蓄積するので、1ダメージずつでも長期平均が崩れない。
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        if (!SlashBladeEnchantmentCompat.isBlade(blade)) return;
        int level = BladeUpgradeData.getLevel(blade, BladeUpgradeType.DURABILITY);
        if (level <= 0) return;

        int current = SlashBladeEnchantmentCompat.getBladeDamage(blade);
        int observed = BladeUpgradeData.getObservedDamage(blade);
        int credit = BladeUpgradeData.getWearCredit(blade);
        if (observed < 0) {
            BladeUpgradeData.setWearState(blade, current, credit);
            return;
        }
        if (current <= observed) {
            if (current != observed) BladeUpgradeData.setWearState(blade, current, credit);
            return;
        }

        int delta = current - observed;
        credit += delta * BladeUpgradeRules.durabilityReductionNumerator(level);
        int refunds = credit / 10;
        credit %= 10;
        for (int i = 0; i < refunds; i++) {
            if (!SlashBladeEnchantmentCompat.repairBladeOnePoint(blade)) break;
        }
        BladeUpgradeData.setWearState(blade, SlashBladeEnchantmentCompat.getBladeDamage(blade), credit);
    }
}
