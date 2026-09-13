import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** 第二段階の元データ。依存なしでPNG・モデル・レシピ・鉱石配置を再現可能に生成する。 */
public final class Phase2AssetGenerator {
    private static Path root;
    private static final String NS = "venusmod:";
    // Images 2.5で作成後、16x16へ最近傍縮小して確定したアイテムテクスチャ。
    // Base64でソース管理し、ビルド時にPNGへ復元するため画像内容を再描画・劣化させない。
    private static final Map<String, String> ITEM_TEXTURES = Map.ofEntries(
            Map.entry("sulfur_crystal", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAB30lEQVR4nMVSTWtTURQ89737+vLS2pX6B/oD6sZtwa3FVWPBRrGK+JGkUE0UqsWC0J0UFEL9WrgSRNCNyyqxuijUih8IUqvWok3yiMEKQpN7Z667QjAVFcRZHs6ZMzOMyD+EGhsO6UTUX12fORi9srbOxZkQhXQH/phg/fsqrSkS5YDV+QRHBv3fJymkA9hvfbT2OqypsboQ0TSzbLer2w2VOKktzrltvTuUSEO29p6QlSdF98uvsF+Y36eRTwcQEfk420WUNVHVPHc4gq34GwpODwXI7w9aLdn1Edq1AhFrjB/t7rk63k3UOgBznNNjnfhQCikiMjq8ZY81JTLWrZbi+QSsmQZMjTAHeGi3h+XZEKhqrDyOeG+qk/l0QGtybJYD1F8m0JLB9p3rfuPzKN+/PulKjzxlnMjyW188JWqqCNe0Rvr7AqXEuU9vfLlYtK0Z5Aa0uXK2yx7p16jMRbT1HprmMTa/erDmMkvXkjw1pLn0MMTEhHg/hZhL+bCmzHghxP1LCVqTY/15BKz5QKxRfxFyYJfCZCa5eR9yKW2e3Y1QuhFxaSbJTEojm9JkHCCT0qw+jXDzQoTcoN4gadvz7F6NSo1y+5Ynq+8CVTjfcMpTis65Ow/ob6rgv+AHGtcQ+qZHuAUAAAAASUVORK5CYII="),
            Map.entry("venesite", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABsElEQVR4nGNgGLSggNn572+fWX/vStf/i2Wy/odLHRMuCV12CQYGf31GBUs5BkYGpv8kGxBfGMPAJLOM4b+hCsMMVV9GkgzIYnb8x/BoDcN/ZlaGNe0LGVhDDBjimWyxegOrAXacigz/3TIZlwe9/f/k94f/jOrCDA4cSgwMDAwMyUx2n3C5hiGRyfYvhLb/+1W1899tpfS/x2Uj/xayuvyLZbL5y8DAwPB75YV/8VB1GC6Yvd2X8Ztq579PDD8Z77x7/V9GlofBpO0PY6Ks9P8vDL8Z/sQs/Mf4t5tBnUWYEasB/1fK/2e/osGwUN7v/7+fvxg/vfnLyBjRzyDIzcK4RNGPkcFMhYHx3B+GC39e/sdqQM2ieQz/Jwv+n/PsHKOmjNz/z5/+/GeIbmH49kWLYfnzGwxPZsz+H9n76P+qf8eZYXowoue7evffsx/eMZ7+eOt/Xm0uw5kJGxnufPvI6KT+97/gD21GjmtlOKMeDoKYzP7+fTnr72KepH8/C7b/jWG2/ruUJ+GfD7PZX4KaYcCfyezvb++kv39Kt/2bzRWLMykTBFUsXuRrJgYAAGK8pRdlDc/IAAAAAElFTkSuQmCC"),
            Map.entry("pressure_alloy_blend", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABp0lEQVR4nGNgGAUEwbOnz//Z2dj/xSXPhE+zn0/Av337DjJY29gxphkbsxJtgKe719/fl7T+VYZxMKxft+a/Nf+i/wJOHj/NTMxwugQF7N617190VNy/nTv2/nv25Nk/DzePv7/eTPi3oVnpr6WZJYohjMgcX2/fv+vb7jLuPajIMH0PG4OKmgbDu7dvGTqzBf+HFJ5gUFZWY3z18vn/N29eMZw4fYoZrvH///+MgT7ey96+fvevpqrx35XDZf+urVL9t6Hf5O++PQf/RYeH/P3ze+nfX+c1/3q6e/+bMmnGP3s7h7/wMGBkZPz/4/f/8OkzZjFcuXKeYfMhsf+H3pUzTN7Ez3jlylWGDx+/M/6/2sL49/cfhtevX/5fsmTB/4OHDjAHB4b8ZWFgYGDw9vT5u3X7FuZZye849Zgef9nyQIohw1f0v+xPLoap27cwblroyeARsfH/5y+fGPT09RmFBIQZGBj+/127fg3CG+Vltf/cXNy/MTAwMJyYLvWHgYGBwcTI5C8DAwNDU6zCXwYGBoaJ6eKwAGQszC/96+rsRlysDG4AAFgLuMogXtMPAAAAAElFTkSuQmCC"),
            Map.entry("pressure_alloy_ingot", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAB90lEQVR4nN2ST0iTcRjHv8/7lhi1Cq3Yu72REtQb/TmMCAMPBW60pBgJdXRES80OHZQC7bRDVIfoULFCZyAFQR1W1KGVa7hCzKW57Z17J+j+OTeUcjFb9r6/DpFoUZ3rc3zg833+8AD/FE1mQf25xv1NarYID89YBX/sUa0q2VyU95qXhdCfOxq0a54JJDOzCIXHwHM8prLTeHuvFV0vs9zSCRaDHHUGtdkiaMX5kua46sfT528QjsQgRxVUV4nYX2PC6jLCQsCiLRNP1wnqTeduCq1x4VX/AESDHiBCMpVmn0tfKBweRffJUYxFPzDJdoiICGdP3GEEAF+LA5r3ciPup+thMu0FA0E06tlkIkXvgkHMFEp43KYw5wsbVVZUIBDox9aiB3JijnEAwEYuwnzuOE5JXuawH4No1IMjjoaGBtFzpRxPejaxW7fjtEPaxuLjcYA4xtOS3YM3TNqz+Vacr32AgD8Dt2JFPj8Nj7sK7o5efNrlRGIyxSKR96Rbux4zwbuMAeiTC99zDkg6dU/9BdouSTiyoQvGw9fR225FLL3AlFVHkc9lIG6uplwui5LiYb7oHP/jdisAwBct8MAldSprpzKdDPvGFvQNfwS/00GzE+NYWb4Oydcu5pMLi+Jv6WzYooa6a7SGfZXqQUn3y+f9h3wDN4zM3dqb+uoAAAAASUVORK5CYII="),
            Map.entry("portable_life_support", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAB/ElEQVR4nLVS30tTcRT/fL93t4S8c9Qlp20uiSx0s/Q2XZSNSUGNin7MqJeIUVQPPfggRhFBEP0Dvc0hWK6IZEWQPdiDoCxzZKzIhCBjFKR5CxcS457vtyej5TIp+rydD+fzOR/OOcA/ghUj99SX0EqVu6Yt6R43dZHNZtNLNjjo115Ew3pdmabApnJ5LrEGjCtIpwaUJUU61GwX1lhYbK1ZIeY5oylErqr1VKyf/0oMvXNI/+k5+XpGQzAIGwC4ZBoPLtj+PH21000Dj4fEzURSRI5ERfU6L7XWlVC50y021Boi07WRDvi1giQFto1OE0rmjGTvZ6FN5+B1WKisCeJ4kwqSwLX+b5iU5QAGf2gKllhR6aGR1DBzzpxC560Anj9LyVVzT3BPD6GlL8byPo/U7Rx5S6J/7KuyYAeKTcXUxzdovzgCzhka9ZdMCoFdd+Msc7ULXGHs/mjONi9eYMAZn7REKQLHYjBNU94Y5DJ6OYmTD5/K0HACJzq65V7DboUbSoteBBM9PlHfsJ3O7taF0byT7Ho19SUf0ZZAK1kfOsSrbi85Kzz5gqE/F/suTc329sTY5sh16fN5WWfbcnz+ksOd3jjwaQKj6hVYZC3+UPH2Krp9fq04uq2MAOBwwEFttVgmxiNiUeHvsCO0n+TbqNhktMi/Mvjv+A4iN8AZaUA1XAAAAABJRU5ErkJggg=="),
            Map.entry("pressure_helmet", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACZUlEQVR4nM1SXUjTcRQ99z81nfkRUWkK879Jfq4sCktTJCiSCgOjFH0qIYJeSiSKPh6UiF4qEfNJIa2IiEqjWEVtMmYfDlqbZoGrJmvKMj/mHJv7/24Porl66aGHztu93Hvu4ZwL/LeoKU4Us+ZdwtCUrWjlHKHV5ip/tajT5Y/XlCQrM/21olyvVrofPRe9Zpvo7jGJrKwC9+/z0tJClnPCFkvviiunNyB+VRCt57dArU5CwXodZvxBHKyqS5XlnAgltLTwfTgpftitsL5zo7REA0ufi0tLZer6VAf3Nw902gxM+2bRcq2Rnc5BVQTB5doUpdmcTMVbd+DN65cMAAxGXNxyNO6foLISGa3WSniMZ5C5pwX1J2okAIhaIGg1J8PbdQurC7MwYugnMewCTCZAr8elxw/RcNuNV813YMMaGDy/rFj04Ev9WQo/60OAAcXYB7bZARCzfQDWvE34mqbDyvQErmsLo7pqHzQaTWyEAnK5AZ8fnW8/AjHRoKnJ+T4zs0pFiI7BtHeKHIM2LPt+jiVJ7QegWlSgudvOPDUJxTMODA1BCGZIEmEuSFEV5chwDXFzhwNj3buxucJCwTBThILCVA8CJgO8SghpViMDRO6dlaDcbGy82IRAfBJYTGBk7VUUJJVxp90XmQIA2p6vU9pPpXObfS/13O/gw0eOkUoCFE7A+wfH4fERhxKLMDo6goUYo5YQ8Isb69j7OUx+3zQUBRQIRbPzaQOcooiE2AZTZyxVHzWyxTl//Y9HyszMY38wJA7pJzCnMMYSD5DD0Y8nF9S4fnOQ7w2kYNg5oMK/xE/zYA4mPLGr5AAAAABJRU5ErkJggg=="),
            Map.entry("pressure_chestplate", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACHklEQVR4nN2SXUiTcRTGn/POmG6Z4MXahrXlcn2ADkzqdWEsQyvQkiCMZhgFIXVX5EV3QRbL6qZPMIQ0HNTYJJuazot06DtWrqhcDropyMpgWtoHcf7/rgr8qNug39XhcM7D4XkO8H/yVTskTKY8nhn0itmhOjabV4ovI/W82Kzyq8jPX8uRC1vE0KDGnz5Ow+FYg40NSVlyZAx5K1bJqQ/TFI3GRL/PLWz2Ap4jEIslOPk8Tm63HdHhGJUdT8k+n5X0+ixkZhoQ8ZnJc/IVolENpe58pJIJWmrMFb/P0LQEJ0afiWs3bgm7vYADwftc4CziniaVw2c2sdNZJEKhMK92rBNXrraKR4+fCqMhlwGAAKDvfBkrEHgYnyCbNUcuKT5FyfFxxLQRKYSEqqpwuQoxM3waE5OfobpMaAm+pEA8rSgAUNk4pHui89KUpVZ6VCuRQnLf3hrZXxym/XVeBO76QZC0c5ud3ubsxpjhIAXiaWWOiRebfdJgMGI0o0HWrh+gF8kUnHtKpSN9nfR6PWps96D9OCyzl2XDd65J/jHCUG+Uy7dWie+zPaLl5m1ubfWz/NbDqlrOd0JdC6Kk+Q3z8jyONBdiLOsoaW3HpE6Xgc31l8mYOisPXHqNd+/f6P4qEAx28y7PJFV7w9i+o0KCFPT1PkBXewV1j9hkdVWlbv7OAtobS4TFbOMOfyd3+DvZYrVx24kNi37iv+cnizPlfYT0rP4AAAAASUVORK5CYII="),
            Map.entry("pressure_leggings", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABfUlEQVR4nNWQzyvDcRzGn89a5jJKfTf2nZohw4imXGUNRfgvODlzk1KchZC12BzmpI3YWEr5nR8pirKYNoSL2GLf99sVk+2kPLf309PT6/0A/17iu8HhHiq2L7OisJAkPd/fxUSWRsMXAbsQ5hHV93yK4XUGeGLKBYulgpPvb8JSXonRsUnhdq5khhRc3SK9zkCFxiIym8tINppIkgrIv7ROP+W/ENiKs982d3YwO+fFttMKrTYHR+4a9njmcXR8glpTtu/XAibBYAYpQKJkHPHXF4rlDQsCg5gBFVIo1J+Pg3BcU397Rw9PjwivRnB+care2NpTcrVaxGJRcXiZ6Ei7gU4qoOurKPn8IQKABV+IIpGootcZ0m8AAHZHGxte+tn8OsgAUJEcQv7zgGh0tHJGBWtBPyBU4vD4BgCwexCGUAGh4GJaegBA8rybrFYbNTnaCQBamjupuqqO3s+6fnwhRbJcqOzPNJKrr0wBgOneUtpzNZCcL2dW8Of6AP/Oo9XJ/pLTAAAAAElFTkSuQmCC"),
            Map.entry("pressure_boots", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABmUlEQVR4nN2RvWtTURyGn5PU0OggFdIm+JF7gya0VnQwGqogxaFKneqklmay5B8QOnSsWgcXsQmJaAZxUaHQv0CLYsAUSyJYAt57KSiCgx0Kvb3JOaeTKbn92PUdX9734fcB/6cMw5RG3JR+zzQ6PYCA39j8MCJjsbiIxk7gfbwuAdzFa6q37xihULfwQ3YA3i3+5nKkoe+e/8Gr198BeLvg8Kmc5MlEGBBiX0CutCYGzEMiEAyI1Z6sAHAOZ3F/WSIy+hLbsTo6XX6A7ViBldWjSiv0N7suAD4v1bH6XMYe3PDHd04AcPXicX3m5mNGohUNcH/U0qmzUZ27pPRu+Q4tFy7IRjktT51MyWSyXwGcHjynMpkrsrl8SyUSCbVn+Us+LaemZ9XtO5OyVRtXpeJzBfCs9EIuVetyanpWVas1ZRqJ9ifaKxhxU775OSaankvwQBdaaZ7OFTTAXL7IzMNHQskWta8rev7ekfYnxDbAkOnMMI7dEO7Ghl5f/4Nt28G/uaGh4ZbX9EQ4fJCm51GpvN/1fv+gtgB8z56snIr6FQAAAABJRU5ErkJggg=="),
            Map.entry("acid_condensate_bucket", "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACCElEQVR4nM2Tu2tTcRTHv+d3702aIekmBV2KQcGLJD4WB8Um2IpJHcR/wVVtcS0dfGBULFioiC7F+hgUpaSmVOimKEJi2iQNKW1Nr7UldlMi3uR3jkNwaG/EwcUzHc7jcx6cA/yj0HaDbUc0QCAQgSAiDIigWJoz/gqw7SgnkmcRDneDqOXq641h6tUMRkdvA8K6VJqz2rZi2xG+ODjMx2MJrn619JNJxY9eKp54obgnnuSF8grfuHWP2yfv289jY+N88nRcNxuG1q7J2jX16zeKtWtwelbp9KzSx04c5WjksBcyMDjMD54Su66pcxVLLzkHuPTJ4kK1Q9frPv5R9+uyY+rCitIXBoa2ABQAZLPvIN8e41Sin0xL6O2HPHIftTA34Lc0fGaDwl1M588lKJd9v6W4CQCbtQ1ZXFpGqDNE+F7G+N1LAISIFIgI0+lJiff1osPvo9rGqngAxVLeICJO3RzB1WspBAIBkFLC3IT7swGmGdq7J4NgMIjpzHPDAwAAgeDz2jouXxlCpbyMVcchIoLjfEFP7DqS/WdQq9U8+1O/lWIxr+6MpLC7exeIgFBnEJZpAqRw8NARsACZqWf6jwAAYObWfERQZEAAGEqBCBDd9FT3ABbK80ZlsQoIQAQwM1oHqWTi4X20u8I2vxDVO7p2wjRNEhYopbC+VsV8Iae2x/4f8gvwIeV7kOIS0QAAAABJRU5ErkJggg==")
    );
    private static Map<String, Object> m(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Odd key/value list");
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            if (map.put(key, pairs[i + 1]) != null) throw new IllegalArgumentException("Duplicate key: " + key);
        }
        return map;
    }
    private static String json(Object value) {
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> list) return list.stream().map(Phase2AssetGenerator::json).collect(Collectors.joining(",", "[", "]"));
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(e -> json(e.getKey()) + ":" + json(e.getValue())).collect(Collectors.joining(",", "{", "}"));
        throw new IllegalArgumentException("Unsupported JSON value " + value);
    }
    private static void document(String path, Object value) throws IOException {
        Path file = root.resolve(path + ".json"); Files.createDirectories(file.getParent()); Files.writeString(file, json(value) + "\n");
    }
    private static void png(String name, BufferedImage image) throws IOException {
        Path file = root.resolve("assets/venusmod/textures/" + name + ".png"); Files.createDirectories(file.getParent());
        if (!ImageIO.write(image, "png", file.toFile())) throw new IOException("PNG encoder missing");
    }
    private static Map<String, Object> item(String name) { return m("item", name); }
    private static Map<String, Object> result(String name, int count) { return m("id", NS + name, "count", count); }
    private static void recipe(String name, Object data) throws IOException { document("data/venusmod/recipe/" + name, data); }
    private static void shaped(String name, List<String> pattern, Map<String, Object> keys, boolean create) throws IOException {
        Map<String, Object> data = m("type", "minecraft:crafting_shaped", "pattern", pattern, "key", keys, "result", result(name, 1));
        if (create) data.put("neoforge:conditions", List.of(m("type", "neoforge:mod_loaded", "modid", "create")));
        recipe(name, data);
    }
    private static void simpleDrop(String name, boolean create) throws IOException {
        Map<String, Object> data = m("type", "minecraft:block", "pools", List.of(m("rolls", 1,
                "conditions", List.of(m("condition", "minecraft:survives_explosion")),
                "entries", List.of(m("type", "minecraft:item", "name", NS + name)))));
        if (create) data.put("neoforge:conditions", List.of(m("type", "neoforge:mod_loaded", "modid", "create")));
        document("data/venusmod/loot_table/blocks/" + name, data);
    }
    private static void ore(String name, String drop, String replace, int veins, int size, int min, int max) throws IOException {
        document("data/venusmod/worldgen/configured_feature/" + name, m("type", "minecraft:ore", "config",
                m("size", size, "discard_chance_on_air_exposure", 0.0, "targets", List.of(m("target",
                        m("predicate_type", "minecraft:tag_match", "tag", replace), "state", m("Name", NS + name))))));
        document("data/venusmod/worldgen/placed_feature/" + name, m("feature", NS + name, "placement", List.of(
                m("type", "minecraft:count", "count", veins), m("type", "minecraft:in_square"),
                m("type", "minecraft:height_range", "height", m("type", "minecraft:uniform", "min_inclusive", m("absolute", min), "max_inclusive", m("absolute", max))),
                m("type", "minecraft:biome"))));
        document("data/venusmod/loot_table/blocks/" + name, m("type", "minecraft:block", "pools", List.of(m("rolls", 1, "entries", List.of(
                m("type", "minecraft:alternatives", "children", List.of(
                        m("type", "minecraft:item", "name", NS + name, "conditions", List.of(m("condition", "minecraft:match_tool", "predicate",
                                m("predicates", m("minecraft:enchantments", List.of(m("enchantments", "minecraft:silk_touch", "levels", m("min", 1)))))))),
                        m("type", "minecraft:item", "name", NS + drop, "functions", List.of(
                                m("function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune", "formula", "minecraft:ore_drops"),
                                m("function", "minecraft:explosion_decay"))))))))));
    }
    private static void data() throws IOException {
        for (String name : List.of("sulfur_crystal", "venesite", "pressure_alloy_blend", "pressure_alloy_ingot", "portable_life_support",
                "pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots", "acid_condensate_bucket")) {
            document("assets/venusmod/models/item/" + name, m("parent", "minecraft:item/generated", "textures", m("layer0", NS + "item/" + name)));
        }
        for (String block : List.of("sulfur_ore", "venesite_ore", "venus_glass")) {
            document("assets/venusmod/blockstates/" + block, m("variants", m("", m("model", NS + "block/" + block))));
            Map<String, Object> model = m("parent", "minecraft:block/cube_all", "textures", m("all", NS + "block/" + block));
            if (block.equals("venus_glass")) model.put("render_type", "minecraft:translucent");
            document("assets/venusmod/models/block/" + block, model);
            document("assets/venusmod/models/item/" + block, m("parent", NS + "block/" + block));
        }
        document("assets/venusmod/models/item/atmospheric_condenser", m("parent", NS + "block/atmospheric_condenser"));
        // 流体は専用レンダラーが描画するので静的モデルは空。
        document("assets/venusmod/blockstates/acid_condensate", m("variants", m("", m("model", "minecraft:block/water"))));
        document("data/venusmod/tags/item/environment_suit", m("replace", false, "values", List.of(NS + "pressure_helmet", NS + "pressure_chestplate", NS + "pressure_leggings", NS + "pressure_boots")));
        document("data/c/tags/item/ingots/pressure_alloy", m("replace", false, "values", List.of(NS + "pressure_alloy_ingot")));
        document("data/minecraft/tags/block/needs_iron_tool", m("replace", false, "values", List.of(NS + "venesite_ore")));
        ore("sulfur_ore", "sulfur_crystal", "minecraft:stone_ore_replaceables", 10, 8, 32, 160);
        ore("venesite_ore", "venesite", "minecraft:deepslate_ore_replaceables", 5, 4, -56, 16);
        simpleDrop("atmospheric_condenser", true); simpleDrop("venus_glass", false);
        shaped("portable_life_support", List.of("IGI", "RBR", "IGI"), m("I", item("minecraft:iron_ingot"), "G", item("minecraft:glass"),
                "R", item("minecraft:redstone"), "B", item("minecraft:gold_block")), false);
        recipe("pressure_alloy_blend", m("type", "minecraft:crafting_shapeless", "ingredients", List.of(item(NS + "nickel_ingot"), item(NS + "nickel_ingot"),
                item("minecraft:iron_ingot"), item("minecraft:iron_ingot"), item(NS + "sulfur_crystal")), "result", result("pressure_alloy_blend", 4)));
        for (String type : List.of("smelting", "blasting")) recipe("pressure_alloy_" + type, m("type", "minecraft:" + type, "ingredient", item(NS + "pressure_alloy_blend"),
                "result", result("pressure_alloy_ingot", 1), "experience", 0.7, "cookingtime", type.equals("smelting") ? 200 : 100));
        Map<String, Object> alloy = m("A", item(NS + "pressure_alloy_ingot"));
        shaped("pressure_helmet", List.of("AAA", "AGA"), m("A", item(NS + "pressure_alloy_ingot"), "G", item("minecraft:glass")), false);
        shaped("pressure_chestplate", List.of("A A", "AAA", "AAA"), alloy, false);
        shaped("pressure_leggings", List.of("AAA", "A A", "A A"), alloy, false);
        shaped("pressure_boots", List.of("A A", "A A"), alloy, false);
        shaped("atmospheric_condenser", List.of("NGN", "AMA", "NCN"), m("N", item(NS + "nickel_ingot"), "G", item("minecraft:glass"),
                "A", item("create:andesite_alloy"), "M", item("create:precision_mechanism"), "C", item("create:andesite_casing")), true);
        recipe("venus_glass", m("type", "minecraft:crafting_shapeless", "ingredients", List.of(item("minecraft:glass"), item("minecraft:glass"), item("minecraft:glass"), item("minecraft:glass"),
                item(NS + "venesite"), item(NS + "acid_condensate_bucket")), "result", result("venus_glass", 8)));
    }
    private static void textures() throws IOException {
        for (String ore : List.of("sulfur_ore", "venesite_ore")) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int hash = Math.floorMod(x * 2347 + y * 137 + x * y * 13, 31);
                int grey = (ore.equals("sulfur_ore") ? 104 : 49) + hash;
                int color = 0xff000000 | grey << 16 | grey << 8 | grey;
                if (((x - 4) * (x - 4) + (y - 4) * (y - 4) < 8) || ((x - 11) * (x - 11) + (y - 10) * (y - 10) < 9) || (x > 2 && x < 6 && y > 11 && y < 14))
                    color = ore.equals("sulfur_ore") ? 0xffd6b023 + (hash << 16) + (hash << 8) : 0xff6b2450 + (hash << 16) + (hash << 8) + hash;
                image.setRGB(x, y, color);
            }
            png("block/" + ore, image);
        }
        BufferedImage glass = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) glass.setRGB(x, y,
                x == 0 || y == 0 || x == 15 || y == 15 ? 0xffa58134 : (x + y == 8 || x + y == 9 || x + y == 24) ? 0x88fff1ae : 0x24edca68);
        png("block/venus_glass", glass);
        for (var entry : ITEM_TEXTURES.entrySet()) {
            Path file = root.resolve("assets/venusmod/textures/item/" + entry.getKey() + ".png");
            Files.createDirectories(file.getParent());
            Files.write(file, Base64.getDecoder().decode(entry.getValue()));
        }
        // Armor model textures are now source-controlled hand-authored assets under
        // src/main/resources/assets/venusmod/textures/models/armor/ to preserve their exact appearance.
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected output resource directory");
        root = Path.of(args[0]); data(); textures();
        System.out.println("Generated phase 2 materials, armor, recipes, worldgen and fixed 16x16 item textures");
    }
}
