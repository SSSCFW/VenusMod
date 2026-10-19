package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoNetwork;
import dev.ssscfw.venusmod.yamato.YamatoTravelData;
import dev.ssscfw.venusmod.yamato.YamatoTravelRules;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 専用サーバー上の実ByteBuf/NBTで、通信の上限・型・復号・所有者入口を検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoNetworkGameTests {
    @GameTest(template = "test/empty")
    public static void validActionRoundTripsWithoutClientClasses(GameTestHelper h) {
        CompoundTag request = new CompoundTag();
        request.putString("name", "金".repeat(YamatoTravelRules.MAX_NAME_LENGTH));
        request.putString("parent", UUID.randomUUID().toString());
        request.putUUID("id", UUID.randomUUID());
        RegistryFriendlyByteBuf buffer = buffer(h);
        try {
            YamatoNetwork.ActionPayload.CODEC.encode(buffer, new YamatoNetwork.ActionPayload("save", request));
            h.assertTrue(buffer.readableBytes() < 32767, "正規要求がC2S上限内");
            YamatoNetwork.ActionPayload decoded = YamatoNetwork.ActionPayload.CODEC.decode(buffer);
            h.assertTrue(decoded.action().equals("save") && decoded.request().equals(request), "最大長の日本語名とUUIDを正確に往復");
            h.assertTrue(!buffer.isReadable(), "要求を余りなく復号");
            decoded.request().putString("name", "変更");
            h.assertTrue(request.getString("name").length() == YamatoTravelRules.MAX_NAME_LENGTH, "復号側の変更を送信元NBTへ伝播しない");
        } finally {
            buffer.release();
        }
        RegistryFriendlyByteBuf empty = buffer(h);
        try {
            empty.writeUtf("refresh", 24);
            FriendlyByteBuf.writeNbt(empty, null);
            h.assertTrue(YamatoNetwork.ActionPayload.CODEC.decode(empty).request().isEmpty(), "NBT終端タグは空の要求として復号");
        } finally {
            empty.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void travelPacketAppliesLatestLifetimeAtomically(GameTestHelper h) throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(owner, "yamato-atomic-settings"), ClientInformation.createDefault());
        Method apply = YamatoNetwork.class.getDeclaredMethod("applyTravelSettings", ServerPlayer.class, CompoundTag.class);
        apply.setAccessible(true);
        YamatoTravelData book = YamatoTravelData.get(player.server);
        for (int mode = 0; mode <= 2; mode++) {
            CompoundTag selected = new CompoundTag();
            selected.putInt("access", mode + 1);
            selected.putInt("lifetime", mode);
            selected.putInt("seconds", mode + 2);
            selected.putDouble("size", 1.0 + mode * 0.5);
            CompoundTag request = new CompoundTag();
            request.putUUID("id", UUID.randomUUID());
            request.put("settings", selected);
            RegistryFriendlyByteBuf buffer = buffer(h);
            try {
                String action = mode == 1 ? "travel_pair" : "travel";
                if (mode == 1) request.putUUID("second", UUID.randomUUID());
                YamatoNetwork.ActionPayload.CODEC.encode(buffer, new YamatoNetwork.ActionPayload(action, request));
                YamatoNetwork.ActionPayload decoded = YamatoNetwork.ActionPayload.CODEC.decode(buffer);
                h.assertTrue(decoded.request().getCompound("settings").equals(selected),
                        "Client travel packet lost new close mode " + mode);
                h.assertTrue((boolean)apply.invoke(null, player, decoded.request()),
                        "Server refused valid close mode in same travel request " + mode);
                h.assertTrue(book.settings(owner).equals(selected),
                        "Server kept old lifetime/seconds when immediately opening mode " + mode);
            } finally {
                buffer.release();
            }
        }
        CompoundTag previous = book.settings(owner);
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("settings", "unlimited");
        h.assertTrue(!(boolean)apply.invoke(null, player, wrongType),
                "Malformed inline settings bypassed server validation");
        CompoundTag invalidMode = new CompoundTag();
        CompoundTag invalid = previous.copy();
        invalid.putInt("lifetime", 3);
        invalidMode.put("settings", invalid);
        h.assertTrue(!(boolean)apply.invoke(null, player, invalidMode),
                "Invalid mode was accepted by the travel handler");
        h.assertTrue(book.settings(owner).equals(previous),
                "Rejected settings partially overwrote the last valid mode");
        h.assertTrue((boolean)apply.invoke(null, player, new CompoundTag())
                && book.settings(owner).equals(previous),
                "Legacy travel requests without inline settings must keep saved settings");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void randomTravelPacketPreservesBothKindsOfSettings(GameTestHelper h) {
        CompoundTag opening = new CompoundTag();
        opening.putInt("access", 6);
        opening.putInt("lifetime", 2);
        opening.putInt("seconds", 180);
        opening.putDouble("size", 3.2D);
        CompoundTag random = new CompoundTag();
        random.putInt("range", 1_000_000);
        random.putInt("mode", 1);
        CompoundTag request = new CompoundTag();
        request.put("settings", opening);
        request.put("randomSettings", random);
        RegistryFriendlyByteBuf buffer = buffer(h);
        try {
            YamatoNetwork.ActionPayload.CODEC.encode(buffer,
                    new YamatoNetwork.ActionPayload("random_travel", request));
            h.assertTrue(buffer.readableBytes() < 32767, "Random settings packet is too large");
            var decoded = YamatoNetwork.ActionPayload.CODEC.decode(buffer);
            h.assertTrue(decoded.action().equals("random_travel")
                    && decoded.request().getCompound("settings").equals(opening)
                    && decoded.request().getCompound("randomSettings").equals(random),
                    "Range, cave mode, lifetime, and size were lost in transport");
            h.assertTrue(!buffer.isReadable(), "Random action payload has unexpected trailing bytes");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void actionDecoderRejectsNonCompoundOversizedAndDeepNbt(GameTestHelper h) {
        RegistryFriendlyByteBuf wrongRoot = buffer(h);
        try {
            wrongRoot.writeUtf("save", 24);
            FriendlyByteBuf.writeNbt(wrongRoot, StringTag.valueOf("compoundではない"));
            expect(h, DecoderException.class, () -> YamatoNetwork.ActionPayload.CODEC.decode(wrongRoot), "非Compoundの根を明示的に拒否");
        } finally {
            wrongRoot.release();
        }

        CompoundTag oversized = new CompoundTag();
        oversized.putByteArray("large", new byte[20 * 1024]);
        RegistryFriendlyByteBuf large = buffer(h);
        try {
            YamatoNetwork.ActionPayload.CODEC.encode(large, new YamatoNetwork.ActionPayload("save", oversized));
            h.assertTrue(large.readableBytes() < 32767, "外側のC2S上限だけでは防げない要求を用意");
            expect(h, NbtAccounterException.class, () -> YamatoNetwork.ActionPayload.CODEC.decode(large), "16KiBのNBT割当上限で拒否");
        } finally {
            large.release();
        }

        RegistryFriendlyByteBuf deep = buffer(h);
        try {
            YamatoNetwork.ActionPayload.CODEC.encode(deep, new YamatoNetwork.ActionPayload("save", nested(20)));
            expect(h, NbtAccounterException.class, () -> YamatoNetwork.ActionPayload.CODEC.decode(deep), "小さな要求でも過剰なネストを拒否");
        } finally {
            deep.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void actionCodecEnforcesActionLengthInBothDirections(GameTestHelper h) {
        RegistryFriendlyByteBuf allowed = buffer(h);
        try {
            String longest = "a".repeat(24);
            YamatoNetwork.ActionPayload.CODEC.encode(allowed, new YamatoNetwork.ActionPayload(longest, new CompoundTag()));
            h.assertTrue(YamatoNetwork.ActionPayload.CODEC.decode(allowed).action().equals(longest), "操作名24文字の境界");
        } finally {
            allowed.release();
        }
        RegistryFriendlyByteBuf outgoing = buffer(h);
        try {
            expect(h, EncoderException.class, () -> YamatoNetwork.ActionPayload.CODEC.encode(outgoing,
                    new YamatoNetwork.ActionPayload("a".repeat(25), new CompoundTag())), "長い操作名を送信側で拒否");
        } finally {
            outgoing.release();
        }
        RegistryFriendlyByteBuf incoming = buffer(h);
        try {
            incoming.writeUtf("a".repeat(25), 64);
            incoming.writeNbt(new CompoundTag());
            expect(h, DecoderException.class, () -> YamatoNetwork.ActionPayload.CODEC.decode(incoming), "偽装した長い操作名を受信側で拒否");
        } finally {
            incoming.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void maximumBookCompressesBelowPacketLimitAndRoundTrips(GameTestHelper h) throws IOException {
        CompoundTag snapshot = maximumSnapshot(h);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(raw)) {
            NbtIo.write(snapshot, output);
        }
        h.assertTrue(raw.size() > 1024 * 1024, "無圧縮では1MiBを超える最大長Unicode名の一覧");
        RegistryFriendlyByteBuf buffer = buffer(h);
        try {
            YamatoNetwork.StatePayload.CODEC.encode(buffer, new YamatoNetwork.StatePayload(0, snapshot, true));
            int bytes = buffer.readableBytes();
            h.assertTrue(bytes < 1024 * 1024, "2048件の一覧全体を1MiB未満へ圧縮");
            YamatoNetwork.StatePayload decoded = YamatoNetwork.StatePayload.CODEC.decode(buffer);
            h.assertTrue(decoded.kind() == 0 && decoded.open(), "一覧種別と画面を開くフラグを維持");
            h.assertTrue(decoded.data().equals(snapshot), "地点名・座標・順序・設定を削らず完全に復元");
            h.assertTrue(!buffer.isReadable(), "圧縮状態を余りなく復号");
            System.out.println("YamatoNetworkGameTests: 2048 entries, raw=" + raw.size() + ", encoded=" + bytes + " bytes");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void fullHistoryAndManualBookFitTogetherWithoutDroppingRecords(GameTestHelper h) {
        UUID owner=UUID.randomUUID();CompoundTag player=maximumSnapshot(h);player.putUUID("owner",owner);
        ListTag players=new ListTag();players.add(player);CompoundTag saved=new CompoundTag();saved.put("players",players);
        YamatoTravelData data=YamatoTravelData.load(saved,h.getLevel().registryAccess());
        Random random=new Random(0x484953544F52594CL);
        for(int i=0;i<YamatoTravelRules.DEFAULT_HISTORY_LIMIT;i++) {
            StringBuilder name=new StringBuilder();
            for(int j=0;j<YamatoTravelRules.MAX_HISTORY_NAME_LENGTH;j++)name.append((char)(0x4E00+random.nextInt(0x5200)));
            String dimension="venusmod:history_"+Long.toUnsignedString(random.nextLong(),16)
                    +Long.toUnsignedString(random.nextLong(),16)+Long.toUnsignedString(random.nextLong(),16);
            h.assertTrue(data.recordOpening(owner,name.toString(),dimension,random.nextDouble()*60000000-30000000,
                    random.nextDouble()*512-64,random.nextDouble()*60000000-30000000,
                    random.nextFloat()*360,random.nextFloat()*180-90),"最大履歴を実SavedDataへ追記");
        }
        CompoundTag snapshot=data.snapshot(owner);ListTag active=new ListTag();
        ListTag entries=snapshot.getList("entries",Tag.TAG_COMPOUND);
        for(int i=0;i<64;i++) {
            CompoundTag source=entries.getCompound(i+1),target=entries.getCompound(i+65),rift=new CompoundTag();
            rift.putUUID("id",UUID.randomUUID());rift.putString("name",source.getString("name"));
            rift.putString("sourceDimension",source.getString("dimension"));rift.putString("targetDimension",target.getString("dimension"));
            for(String axis:new String[]{"x","y","z"}) {
                rift.putDouble("source"+axis.toUpperCase(java.util.Locale.ROOT),source.getDouble(axis));
                rift.putDouble("target"+axis.toUpperCase(java.util.Locale.ROOT),target.getDouble(axis));
            }
            rift.putInt("access",6);rift.putInt("lifetime",1);rift.putInt("seconds",180);rift.putInt("remaining",179);
            rift.putDouble("size",5);rift.putFloat("yaw",source.getFloat("yaw"));rift.putFloat("pitch",source.getFloat("pitch"));active.add(rift);
        }
        snapshot.put("active",active);
        h.assertTrue(entries.size()==2048&&snapshot.getList("history",Tag.TAG_COMPOUND).size()==1000,"手動保存と履歴の上限を独立して保持");
        RegistryFriendlyByteBuf buffer=buffer(h);
        try {
            YamatoNetwork.StatePayload.CODEC.encode(buffer,new YamatoNetwork.StatePayload(0,snapshot,true));
            int bytes=buffer.readableBytes();
            h.assertTrue(bytes<900*1024,"2048手動+1000履歴+64開放中でも圧縮制限内");
            h.assertTrue(YamatoNetwork.StatePayload.CODEC.decode(buffer).data().equals(snapshot),"全履歴・手動保存・開放中を削らず往復");
            System.out.println("YamatoNetworkGameTests: 2048 manual + 1000 history + 64 active, encoded="+bytes+" bytes");
        } finally {buffer.release();}
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void stateDecoderRejectsCompressedSizeDepthAndDeclaredAllocation(GameTestHelper h) throws IOException {
        RegistryFriendlyByteBuf declaredLarge = buffer(h);
        try {
            declaredLarge.writeVarInt(0);
            declaredLarge.writeBoolean(false);
            declaredLarge.writeVarInt(900 * 1024 + 1);
            expect(h, DecoderException.class, () -> YamatoNetwork.StatePayload.CODEC.decode(declaredLarge), "圧縮配列のサイズ宣言を割当前に拒否");
        } finally {
            declaredLarge.release();
        }

        RegistryFriendlyByteBuf deep = buffer(h);
        try {
            YamatoNetwork.StatePayload.CODEC.encode(deep, new YamatoNetwork.StatePayload(0, nested(20), false));
            expect(h, NbtAccounterException.class, () -> YamatoNetwork.StatePayload.CODEC.decode(deep), "圧縮後もネスト上限を適用");
        } finally {
            deep.release();
        }

        // 実際に33MiBを割り当てず、NBTヘッダの巨大な配列宣言で割当保護を検査する。
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(compressed))) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("");
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("bomb");
            output.writeInt(33 * 1024 * 1024);
        }
        RegistryFriendlyByteBuf allocation = buffer(h);
        try {
            allocation.writeVarInt(0);
            allocation.writeBoolean(false);
            allocation.writeByteArray(compressed.toByteArray());
            expect(h, NbtAccounterException.class, () -> YamatoNetwork.StatePayload.CODEC.decode(allocation), "小さい圧縮データの巨大な復号割当を拒否");
        } finally {
            allocation.release();
        }

        RegistryFriendlyByteBuf malformed = buffer(h);
        try {
            malformed.writeVarInt(0);
            malformed.writeBoolean(false);
            malformed.writeByteArray(new byte[]{1, 2, 3});
            expect(h, UncheckedIOException.class, () -> YamatoNetwork.StatePayload.CODEC.decode(malformed), "壊れた圧縮ストリームを拒否");
        } finally {
            malformed.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void stateEncoderRejectsAnOversizedCompressedPayload(GameTestHelper h) {
        byte[] randomBytes = new byte[930 * 1024];
        new Random(0x59414D41544FL).nextBytes(randomBytes);
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("noise", randomBytes);
        RegistryFriendlyByteBuf buffer = buffer(h);
        try {
            expect(h, IllegalArgumentException.class, () -> YamatoNetwork.StatePayload.CODEC.encode(buffer,
                    new YamatoNetwork.StatePayload(0, payload, false)), "圧縮できない巨大状態を送信前に拒否");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void cutCodecRetainsBothHandsAndCancellationStage(GameTestHelper h) {
        for (int hand : new int[]{0, 1}) for (int stage : new int[]{-1, 0, 1}) {
            RegistryFriendlyByteBuf buffer = buffer(h);
            try {
                YamatoNetwork.CutPayload original = new YamatoNetwork.CutPayload(123456, hand, stage);
                YamatoNetwork.CutPayload.CODEC.encode(buffer, original);
                h.assertTrue(YamatoNetwork.CutPayload.CODEC.decode(buffer).equals(original), "主手・副手・中断通知の完全な往復");
                h.assertTrue(!buffer.isReadable(), "演出通知を余りなく復号");
            } finally {
                buffer.release();
            }
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void voidSlashFreezePacketsRoundTrip(GameTestHelper h) {
        for (int entityId : new int[]{1, 42, 32767, 2147483647}) {
            for (boolean frozen : new boolean[]{false, true}) {
                RegistryFriendlyByteBuf buffer = buffer(h);
                try {
                    YamatoNetwork.FreezePayload original = new YamatoNetwork.FreezePayload(entityId, frozen);
                    YamatoNetwork.FreezePayload.CODEC.encode(buffer, original);
                    h.assertTrue(YamatoNetwork.FreezePayload.CODEC.decode(buffer).equals(original),
                            "Entity IDと拘束・解除の送受信");
                    h.assertTrue(!buffer.isReadable(), "拘束通知の余りのない復号");
                } finally {
                    buffer.release();
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void packetIdsRejectShorthandAndMalformedTypes(GameTestHelper h) {
        UUID expected = UUID.fromString("12345678-1234-4567-890a-123456789abc");
        CompoundTag request = new CompoundTag();
        request.putUUID("id", expected);
        h.assertTrue(expected.equals(YamatoNetwork.readId(request)), "NBTのUUID表現");
        request.putString("id", expected.toString());
        h.assertTrue(expected.equals(YamatoNetwork.readId(request)), "正規文字列のUUID表現");
        request.putString("id", "1-1-1-1-1");
        h.assertTrue(YamatoNetwork.readId(request) == null, "短縮UUIDを別IDに読み替えない");
        request.putIntArray("id", new int[]{1, 2, 3});
        h.assertTrue(YamatoNetwork.readId(request) == null, "長さが違うUUID配列を拒否");
        request.putInt("id", 0);
        h.assertTrue(YamatoNetwork.readId(request) == null, "整数のIDを拒否");
        request.remove("id");
        h.assertTrue(YamatoNetwork.readId(request) == null, "欠落したIDを拒否");
        request.putUUID("second",expected);
        h.assertTrue(expected.equals(YamatoNetwork.readId(request,"second")),"2地点目もUUIDで解決");
        request.putString("second","1-1-1-1-1");
        h.assertTrue(YamatoNetwork.readId(request,"second")==null,"2地点目も短縮UUIDを拒否");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void serverHandlerRejectsAnvilNamedSword(GameTestHelper h) throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(owner, "yamato-net-test"), ClientInformation.createDefault());
        ItemStack imitation = new ItemStack(Items.IRON_SWORD);
        imitation.set(DataComponents.CUSTOM_NAME, Component.literal("閻魔刀 / Yamato"));
        player.setItemInHand(InteractionHand.MAIN_HAND, imitation);
        YamatoTravelData data = YamatoTravelData.get(player.server);
        CompoundTag request = new CompoundTag();
        request.putString("name", "通信権限の試験地点");
        request.putString("parent", "");
        h.assertTrue(data.apply(player, "save", request), "所有者の既存地点を用意");
        CompoundTag before = data.snapshot(owner);
        CompoundTag deletion = new CompoundTag();
        deletion.putUUID("id", before.getList("entries", Tag.TAG_COMPOUND).getCompound(0).getUUID("id"));
        try {
            var handle = YamatoNetwork.class.getDeclaredMethod("handle", ServerPlayer.class, YamatoNetwork.ActionPayload.class);
            handle.setAccessible(true);
            try {
                handle.invoke(null, player, new YamatoNetwork.ActionPayload("delete", deletion));
            } catch (InvocationTargetException exception) {
                throw new AssertionError("サーバーの要求処理で例外", exception.getCause());
            }
            h.assertTrue(data.snapshot(owner).equals(before), "表示名だけが閻魔刀の剣では保存一覧を編集できない");
            CompoundTag pairRequest=deletion.copy();pairRequest.putUUID("second",UUID.randomUUID());
            handle.invoke(null,player,new YamatoNetwork.ActionPayload("travel_pair",pairRequest));
            h.assertTrue(data.snapshot(owner).equals(before),"2地点接続でも偽の刀から履歴や状態を変更しない");
        } finally {
            data.apply(player, "delete", deletion);
        }
        h.succeed();
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper h) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
    }

    private static void expect(GameTestHelper h, Class<? extends Throwable> type, Runnable action, String reason) {
        Throwable caught = null;
        try {
            action.run();
        } catch (RuntimeException exception) {
            caught = exception;
        }
        h.assertTrue(type.isInstance(caught), reason + " (expected=" + type.getSimpleName()
                + ", actual=" + (caught == null ? "none" : caught.getClass().getSimpleName()) + ")");
    }

    private static CompoundTag nested(int depth) {
        CompoundTag root = new CompoundTag();
        CompoundTag cursor = root;
        for (int i = 0; i < depth; i++) {
            CompoundTag child = new CompoundTag();
            cursor.put("child", child);
            cursor = child;
        }
        return root;
    }

    private static CompoundTag maximumSnapshot(GameTestHelper h) {
        Random random = new Random(0x59414D41544FL);
        UUID owner = new UUID(random.nextLong(), random.nextLong());
        UUID folder = new UUID(random.nextLong(), random.nextLong());
        ListTag entries = new ListTag();
        for (int i = 0; i < YamatoTravelRules.MAX_ENTRIES; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", i == 0 ? folder : new UUID(random.nextLong(), random.nextLong()));
            entry.putString("parent", i == 0 ? "" : folder.toString());
            StringBuilder name = new StringBuilder(YamatoTravelRules.MAX_NAME_LENGTH);
            for (int j = 0; j < YamatoTravelRules.MAX_NAME_LENGTH; j++) name.append((char)(0x4E00 + random.nextInt(0x5200)));
            entry.putString("name", name.toString());
            entry.putBoolean("folder", i == 0);
            entry.putString("dimension", "venusmod:dimension_" + Long.toUnsignedString(random.nextLong(), 16)
                    + Long.toUnsignedString(random.nextLong(), 16) + Long.toUnsignedString(random.nextLong(), 16));
            entry.putDouble("x", random.nextDouble() * 60000000.0D - 30000000.0D);
            entry.putDouble("y", random.nextDouble() * 512.0D - 64.0D);
            entry.putDouble("z", random.nextDouble() * 60000000.0D - 30000000.0D);
            entry.putFloat("yaw", random.nextFloat() * 360.0F);
            entry.putFloat("pitch", random.nextFloat() * 180.0F - 90.0F);
            entry.putLong("created", 1700000000000L + i);
            entries.add(entry);
        }
        CompoundTag player = new CompoundTag();
        player.putUUID("owner", owner);
        player.put("entries", entries);
        ListTag players = new ListTag();
        players.add(player);
        CompoundTag saved = new CompoundTag();
        saved.put("players", players);
        CompoundTag snapshot = YamatoTravelData.load(saved, h.getLevel().registryAccess()).snapshot(owner);
        h.assertTrue(snapshot.getList("entries", Tag.TAG_COMPOUND).size() == YamatoTravelRules.MAX_ENTRIES, "実SavedDataで2048件の最大長名を受理");
        return snapshot;
    }
}
