import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** 外部ライブラリ不要の空GameTestテンプレート。広い移動試験も囲いの内側で完結させる。 */
public final class VerificationAssets {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]).resolve("data/venusmod/structure/test");
        writeFixture(directory.resolve("empty.nbt"), 7, 7, 7);
        writeFixture(directory.resolve("wide_empty.nbt"), 16, 9, 16);
    }

    private static void writeFixture(Path file, int width, int height, int depth) throws Exception {
        Files.createDirectories(file.getParent());
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(3955);
            out.writeByte(9); out.writeUTF("size"); out.writeByte(3); out.writeInt(3);
            out.writeInt(width); out.writeInt(height); out.writeInt(depth);
            out.writeByte(9); out.writeUTF("palette"); out.writeByte(10); out.writeInt(1);
            out.writeByte(8); out.writeUTF("Name"); out.writeUTF("minecraft:air"); out.writeByte(0);
            for (String name : new String[]{"blocks", "entities"}) {
                out.writeByte(9); out.writeUTF(name); out.writeByte(10); out.writeInt(0);
            }
            out.writeByte(0);
        }
    }
}
