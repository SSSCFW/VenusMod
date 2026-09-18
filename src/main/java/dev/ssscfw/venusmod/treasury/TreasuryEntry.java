package dev.ssscfw.venusmod.treasury;

import net.minecraft.world.item.ItemStack;

/** GUIへ渡す論理スタックのスナップショット。保護状態は刀のComponentから読む。 */
public final class TreasuryEntry {
    private final ItemStack template;
    private final int count;

    public TreasuryEntry(ItemStack template, int count) {
        this.template = template == null || template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
        this.count = Math.max(0, Math.min(TreasuryRules.MAX_LOGICAL_STACK, count));
    }
    public TreasuryEntry(ItemStack template, int count, boolean favorite) {
        this(template, count, favorite, false);
    }
    public TreasuryEntry(ItemStack template, int count, boolean favorite, boolean phantasmProtected) {
        this(template, count);
        TreasuryBladeProtection.migrate(this.template, favorite, phantasmProtected);
    }
    public ItemStack template() { return template.copy(); }
    public int count() { return count; }
    public boolean favorite() { return TreasuryBladeProtection.get(template) == 1; }
    public boolean phantasmProtected() { return TreasuryBladeProtection.get(template) == 2; }
}
