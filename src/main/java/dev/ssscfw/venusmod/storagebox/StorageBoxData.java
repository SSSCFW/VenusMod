package dev.ssscfw.venusmod.storagebox;

import dev.ssscfw.venusmod.registry.ModDataComponents;
import dev.ssscfw.venusmod.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Storage Boxの全状態変更を集約する。呼び出し側は収納数を直接Componentへ書かない。 */
public final class StorageBoxData {
    public static final TagKey<Item> BLACKLIST = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("venusmod", "storage_box_blacklist"));

    private StorageBoxData() {}

    public static ItemStack template(ItemStack box) {
        if (box == null || box.isEmpty()) return ItemStack.EMPTY;
        ItemStack stored = box.get(ModDataComponents.STORAGE_BOX_TEMPLATE.get());
        return stored == null || stored.isEmpty() ? ItemStack.EMPTY : stored.copyWithCount(1);
    }

    public static int storedCount(ItemStack box) {
        if (box == null || box.isEmpty() || template(box).isEmpty()) return 0;
        return StorageBoxRules.normalizeCount(box.getOrDefault(ModDataComponents.STORAGE_BOX_COUNT.get(), 0));
    }

    public static boolean autoCollect(ItemStack box) {
        return box != null && !box.isEmpty()
                && box.getOrDefault(ModDataComponents.STORAGE_BOX_AUTO_COLLECT.get(), true);
    }

    public static boolean canAccept(ItemStack box, ItemStack incoming) {
        if (box == null || box.isEmpty() || incoming == null || incoming.isEmpty()) return false;
        if (incoming.is(ModItems.STORAGE_BOX.get()) || incoming.is(BLACKLIST)) return false;
        ItemStack current = template(box);
        return current.isEmpty() || ItemStack.isSameItemSameComponents(current, incoming);
    }

    /** incoming自体は変更しない。実際に受け入れた個数を返す。 */
    public static int insert(ItemStack box, ItemStack incoming, int requested) {
        if (!canAccept(box, incoming)) return 0;
        int request = Math.min(Math.max(0, requested), incoming.getCount());
        int accepted = StorageBoxRules.accepted(storedCount(box), request);
        if (accepted <= 0) return 0;
        if (template(box).isEmpty()) {
            box.set(ModDataComponents.STORAGE_BOX_TEMPLATE.get(), incoming.copyWithCount(1));
        }
        box.set(ModDataComponents.STORAGE_BOX_COUNT.get(), storedCount(box) + accepted);
        return accepted;
    }

    /** requestedに関わらず通常の最大スタック数を超えて1回に取り出さない。 */
    public static ItemStack extract(ItemStack box, int requested) {
        ItemStack current = template(box);
        int stored = storedCount(box);
        if (current.isEmpty() || stored <= 0) return ItemStack.EMPTY;
        int take = StorageBoxRules.extractable(stored, requested, current.getMaxStackSize());
        if (take <= 0) return ItemStack.EMPTY;
        ItemStack result = current.copyWithCount(take);
        int remaining = stored - take;
        if (remaining <= 0) {
            box.remove(ModDataComponents.STORAGE_BOX_TEMPLATE.get());
            box.remove(ModDataComponents.STORAGE_BOX_COUNT.get());
        } else {
            box.set(ModDataComponents.STORAGE_BOX_COUNT.get(), remaining);
        }
        return result;
    }

    public static boolean toggleAutoCollect(ItemStack box) {
        boolean enabled = !autoCollect(box);
        box.set(ModDataComponents.STORAGE_BOX_AUTO_COLLECT.get(), enabled);
        return enabled;
    }

    public static double largeChestEquivalent(ItemStack box) {
        ItemStack current = template(box);
        return current.isEmpty() ? 0.0D
                : StorageBoxRules.largeChestEquivalent(storedCount(box), current.getMaxStackSize());
    }
}
