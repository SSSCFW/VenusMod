package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.registry.ModDataComponents;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.storagebox.StorageBoxData;
import dev.ssscfw.venusmod.storagebox.StorageBoxRules;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    @GameTest(template = "test/empty")
    public static void templateUsesValueEqualityAndDefensiveCopies(GameTestHelper h) {
        ItemStack original = new ItemStack(Items.DIAMOND, 32);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("immutable-template"));
        ItemStack first = new ItemStack(ModItems.STORAGE_BOX.get());
        ItemStack second = new ItemStack(ModItems.STORAGE_BOX.get());
        h.assertTrue(StorageBoxData.insert(first, original, 32) == 32
                && StorageBoxData.insert(second, original.copy(), 32) == 32, "別個体から同じ収納状態を作成");
        var firstValue = first.get(ModDataComponents.STORAGE_BOX_TEMPLATE.get());
        var secondValue = second.get(ModDataComponents.STORAGE_BOX_TEMPLATE.get());
        h.assertTrue(firstValue != null && firstValue.equals(secondValue), "テンプレートComponentは参照ではなく内容で等価");
        int hash = firstValue.hashCode();
        h.assertTrue(hash == secondValue.hashCode(), "等しいComponentのhashCodeは等しい");
        h.assertTrue(ItemStack.isSameItemSameComponents(first, second), "同じ中身を持つ箱のComponent比較も一致");

        original.set(DataComponents.CUSTOM_NAME, Component.literal("changed-input"));
        original.setCount(1);
        ItemStack returned = StorageBoxData.template(first);
        returned.set(DataComponents.CUSTOM_NAME, Component.literal("changed-copy"));
        returned.shrink(1);
        firstValue.copyOne().set(DataComponents.CUSTOM_NAME, Component.literal("changed-component-copy"));
        h.assertTrue(StorageBoxData.template(first).getHoverName().getString().equals("immutable-template"),
                "入力・取得したItemStackの変更が保存Componentへ伝播しない");
        h.assertTrue(StorageBoxData.storedCount(first) == 32, "コピー変更で論理個数が変わらない");
        h.assertTrue(firstValue.hashCode() == hash && firstValue.equals(secondValue), "外部変更後も等価性とハッシュが安定");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void immutableTemplateKeepsLegacyNbtAndNetworkShape(GameTestHelper h) {
        ItemStack template = new ItemStack(Items.DIAMOND);
        template.set(DataComponents.CUSTOM_NAME, Component.literal("legacy-template"));
        var operations = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag oldTemplate = ItemStack.SINGLE_ITEM_CODEC.encodeStart(operations, template).getOrThrow();
        CompoundTag oldBox = (CompoundTag)new ItemStack(ModItems.STORAGE_BOX.get()).save(h.getLevel().registryAccess());
        CompoundTag components = new CompoundTag();
        components.put("venusmod:storage_box_template", oldTemplate);
        components.putInt("venusmod:storage_box_count", 37);
        components.putBoolean("venusmod:storage_box_auto_collect", false);
        oldBox.put("components", components);

        ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), oldBox);
        h.assertTrue(!loaded.isEmpty() && StorageBoxData.storedCount(loaded) == 37, "従来のItemStack形状で保存された箱を読み込む");
        h.assertTrue(ItemStack.isSameItemSameComponents(StorageBoxData.template(loaded), template), "従来の全Componentを維持");
        h.assertTrue(!StorageBoxData.autoCollect(loaded), "従来の自動回収設定も維持");
        CompoundTag savedAgain = (CompoundTag)loaded.save(h.getLevel().registryAccess());
        h.assertTrue(oldTemplate.equals(savedAgain.getCompound("components").get("venusmod:storage_box_template")),
                "保存形式をコンテナのslotリストへ変更しない");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, template);
            var wrapped = ModDataComponents.STORAGE_BOX_TEMPLATE.get().streamCodec().decode(buffer);
            h.assertTrue(ItemStack.isSameItemSameComponents(wrapped.copyOne(), template) && !buffer.isReadable(),
                    "従来のItemStack通信形式からラッパーを復号");
            buffer.clear();
            ModDataComponents.STORAGE_BOX_TEMPLATE.get().streamCodec().encode(buffer, wrapped);
            ItemStack oldReader = ItemStack.STREAM_CODEC.decode(buffer);
            h.assertTrue(ItemStack.isSameItemSameComponents(oldReader, template) && oldReader.getCount() == 1
                    && !buffer.isReadable(), "ラッパーの出力を従来のItemStack通信形式で復号");
        } finally {
            buffer.release();
        }
        h.succeed();
    }
}
