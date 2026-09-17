package dev.ssscfw.venusmod.treasury;

import net.minecraft.world.item.ItemStack;

/** GUIへ渡す論理スタックのスナップショット。お気に入りは刀のComponentを変更しない。 */
public final class TreasuryEntry {
    private final ItemStack template;
    private final int count;
    private final boolean favorite;

    public TreasuryEntry(ItemStack template, int count) {
        this(template, count, false);
    }

    public TreasuryEntry(ItemStack template, int count, boolean favorite) {
        this.template = template == null || template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
        this.count = Math.max(0, Math.min(TreasuryRules.MAX_LOGICAL_STACK, count));
        this.favorite = favorite;
    }

    public ItemStack template() { return template.copy(); }
    public int count() { return count; }
    public boolean favorite() { return favorite; }
}
