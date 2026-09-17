import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** 自作の鍵アイコンと王の財宝専用リソースだけを生成する。 */
public final class KingsTreasureAssets {
    public static void main(String[] args) throws IOException {
        Path root = Path.of(args[0]);
        write(root, "assets/venusmod/models/item/kings_treasure.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"venusmod:item/kings_treasure"}}
                """);
        write(root, "data/venusmod/recipe/kings_treasure.json", """
                {"type":"minecraft:crafting_shaped","category":"equipment","pattern":[" G ","GEG"," G "],"key":{"G":{"item":"minecraft:gold_ingot"},"E":{"item":"minecraft:ender_eye"}},"result":{"id":"venusmod:kings_treasure","count":1}}
                """);
        write(root, "data/venusmod/damage_type/royal_blade.json", """
                {"message_id":"magic","scaling":"when_caused_by_living_non_player","exhaustion":0.1}
                """);
        for (String tag : new String[]{"bypasses_cooldown", "is_projectile", "is_explosion"}) {
            write(root, "data/minecraft/tags/damage_type/" + tag + ".json", """
                    {"replace":false,"values":["venusmod:royal_blade"]}
                    """);
        }
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // 金色の円環と鍵の歯。第三者の画像アセットを転用しない。
        for (int y = 1; y < 15; y++) for (int x = 1; x < 15; x++) {
            double ring = Math.hypot(x - 7.5, y - 5.0);
            boolean shaft = x >= 7 && x <= 8 && y >= 8;
            boolean tooth = (y == 11 || y == 14) && x >= 8 && x <= 11;
            if ((ring >= 3.0 && ring <= 4.0) || shaft || tooth) icon.setRGB(x, y, (x + y) % 3 == 0 ? 0xffffefae : 0xffffbc29);
        }
        Path path = root.resolve("assets/venusmod/textures/item/kings_treasure.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(icon, "PNG", path.toFile());
    }
    private static void write(Path root, String name, String contents) throws IOException {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
