package dev.ssscfw.venusmod.treasury;

import net.minecraft.world.item.ItemStack;

/** GUIへ渡す王の宝物庫の論理スタックの不変スナップショット。 */
public final class TreasuryEntry {
    private final ItemStack template;
    private final int count;

    public TreasuryEntry(ItemStack template, int count) {
        this.template = template == null || template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
        this.count = Math.max(0, Math.min(TreasuryRules.MAX_LOGICAL_STACK, count));
    }

    public ItemStack template() {
        return template.copy();
    }

    public int count() {
        return count;
    }
}
