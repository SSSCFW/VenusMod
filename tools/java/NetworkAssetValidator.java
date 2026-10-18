import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** JDKだけで生成リソースの参照、画像、面設定を検査する。 */
public final class NetworkAssetValidator {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);int images=0,json=0;
        Pattern reference=Pattern.compile("\\\"venusmod:(block|item)/network/([^\\\"]+)\\\"");
        Pattern coordinates=Pattern.compile("\\\"(?:from|to)\\\":\\[([^]]+)\\]");
        try(var walk=Files.walk(root)) {
            for(Path path:walk.filter(Files::isRegularFile).toList()) {
                String name=path.toString();
                if(name.endsWith(".png")) {
                    BufferedImage image=ImageIO.read(path.toFile());
                    require(image!=null&&image.getWidth()==16&&image.getHeight()==16,"PNG寸法: "+path);images++;
                } else if(name.endsWith(".json")) {
                    String text=Files.readString(path);json++;
                    require(!text.contains("cullface"),"細い機器・ケーブルの面を消さない: "+path);
                    Matcher m=reference.matcher(text);
                    while(m.find()) {
                        String ref=m.group(1)+"/network/"+m.group(2);
                        require(Files.exists(root.resolve("assets/venusmod/models/"+ref+".json"))||Files.exists(root.resolve("assets/venusmod/textures/"+ref+".png")),"未解決参照: "+ref);
                    }
                    m=coordinates.matcher(text);
                    while(m.find())for(String component:m.group(1).split(",")){double n=Double.parseDouble(component);require(n>=0&&n<=16,"頂点範囲: "+path);}
                    if(name.endsWith("_core.json"))require(text.contains("\"from\":[6.0,6.0,6.0]")&&text.contains("\"to\":[10.0,10.0,10.0]"),"芯の太さ4/16: "+path);
                }
            }
        }
        // 無染色8種 + 8種類×16色 = 136種のケーブルアセットを検証。
        require(images==74+8*16,"PNG数: "+images);require(json>=1500,"JSON数: "+json);
        String[] colors={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
        String[] grades={"cat5e_cable","cat6_cable","cat6a_cable","cat8_cable","om3_fiber_cable","om4_fiber_cable","os2_fiber_cable","venus_fiber_cable"};
        for(String grade:grades){
            String tag=Files.readString(root.resolve("data/venusmod/tags/item/network_cables/"+grade+".json"));
            require(tag.contains("venusmod:"+grade),"無染色ケーブルの染色対象: "+grade);
            for(String color:colors){
                String id=grade+"_"+color;
                require(tag.contains("venusmod:"+id),"色替え対象一覧: "+id);
                require(Files.exists(root.resolve("assets/venusmod/textures/block/network/"+id+".png")),"染色テクスチャ: "+id);
                require(Files.exists(root.resolve("assets/venusmod/blockstates/"+id+".json")),"染色ブロックステート: "+id);
                require(Files.exists(root.resolve("assets/venusmod/models/item/"+id+".json")),"染色アイテム: "+id);
                String recipe=Files.readString(root.resolve("data/venusmod/recipe/"+id+".json"));
                require(recipe.contains("minecraft:"+color+"_dye")&&recipe.contains("venusmod:network_cables/"+grade),"染色レシピ: "+id);
            }
        }
        // 機種別モデルと説明を静的検査。染色ケーブルの検査とは独立。
        String[] deviceKinds={"network_pc","network_l2_switch","network_l3_switch",
                "network_router","network_onu","network_olt","network_server","network_redstone_terminal"};
        Set<String> deviceGeometries=new HashSet<>();
        Pattern geometryCoord=Pattern.compile("\"(?:from|to)\":\\[([^]]+)\\]");
        for(String device:deviceKinds){
            Path normal=root.resolve("assets/venusmod/models/block/network/"+device+".json");
            Path fault=root.resolve("assets/venusmod/models/block/network/"+device+"_fault.json");
            require(Files.exists(normal)&&Files.exists(fault),"機種別モデル: "+device);
            List<String> coords=new ArrayList<>();
            Matcher found=geometryCoord.matcher(Files.readString(normal));
            while(found.find())coords.add(found.group(1));
            Collections.sort(coords);
            require(deviceGeometries.add(String.join(";",coords)),"重複する機器形状: "+device);
            for(String locale:List.of("ja_jp","en_us")){
                String text=Files.readString(root.resolve("assets/venusmod_network/lang/"+locale+".json"));
                require(text.contains("\"tooltip.venusmod.network."+device+"\":"),
                        "機器の説明がない: "+locale+"/"+device);
            }
        }
        System.out.println("NetworkAssetValidator: "+json+" JSON / "+images+" PNG / all references resolved");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
