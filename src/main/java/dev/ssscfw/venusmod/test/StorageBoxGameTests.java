package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.registry.ModDataComponents;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.storagebox.StorageBoxData;
import dev.ssscfw.venusmod.storagebox.StorageBoxRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Storage BoxのComponent一致、保存、容量端点を実Minecraft ItemStackで検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorageBoxGameTests {
    @GameTest(template = "test/empty")
    public static void componentsAndSerialization(GameTestHelper h) {
        ItemStack box = new ItemStack(ModItems.STORAGE_BOX.get());
        ItemStack named = new ItemStack(Items.DIAMOND, 32);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("stored-diamond"));

        h.assertTrue(StorageBoxData.insert(box, named, named.getCount()) == 32, "最初のアイテムを収納");
        h.assertTrue(StorageBoxData.storedCount(box) == 32, "個数保存");
        h.assertTrue(StorageBoxData.template(box).getHoverName().getString().equals("stored-diamond"),
                "全Componentsをテンプレートへ保存");

        ItemStack plain = new ItemStack(Items.DIAMOND, 1);
        h.assertTrue(!StorageBoxData.canAccept(box, plain), "同じIDでもComponents違いを混ぜない");

        CompoundTag saved = (CompoundTag) box.save(h.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
        h.assertTrue(StorageBoxData.storedCount(loaded) == 32, "ItemStack保存後も個数維持");
        h.assertTrue(ItemStack.isSameItemSameComponents(
                StorageBoxData.template(box), StorageBoxData.template(loaded)), "テンプレート保存");
        h.assertTrue(StorageBoxData.autoCollect(loaded), "自動回収初期値ON");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void extractionCapacityAndBlacklist(GameTestHelper h) {
        ItemStack box = new ItemStack(ModItems.STORAGE_BOX.get());
        ItemStack stone = new ItemStack(Items.STONE, 64);
        h.assertTrue(StorageBoxData.insert(box, stone, 64) == 64, "石64個を収納");
        ItemStack extracted = StorageBoxData.extract(box, Integer.MAX_VALUE);
        h.assertTrue(extracted.is(Items.STONE) && extracted.getCount() == 64, "最大1スタック取り出し");
        h.assertTrue(StorageBoxData.storedCount(box) == 0 && StorageBoxData.template(box).isEmpty(), "空箱へ戻る");

        h.assertTrue(!StorageBoxData.canAccept(box, new ItemStack(ModItems.STORAGE_BOX.get())),
                "Storage Box自身は入れ子禁止");

        h.assertTrue(StorageBoxData.insert(box, stone, 1) == 1, "容量端点用テンプレート設定");
        box.set(ModDataComponents.STORAGE_BOX_COUNT.get(), Integer.MAX_VALUE - 1);
        h.assertTrue(StorageBoxData.insert(box, stone, 64) == 1, "Integer.MAX_VALUEで容量を停止");
        h.assertTrue(StorageBoxData.storedCount(box) == StorageBoxRules.CAPACITY, "最大容量");
        h.assertTrue(StorageBoxData.insert(box, stone, 1) == 0, "満杯後は追加不可");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void emptyBoxAutoCollectPolicy(GameTestHelper h) {
        ItemStack box = new ItemStack(ModItems.STORAGE_BOX.get());
        h.assertTrue(StorageBoxData.autoCollect(box), "自動回収は初期ON");
        h.assertTrue(StorageBoxData.template(box).isEmpty(), "空箱はテンプレート未設定");
        // Pickupイベント側はtemplate.isEmpty()を明示的に除外する。空箱を拾得物で初期化しない。
        h.succeed();
    }
}
