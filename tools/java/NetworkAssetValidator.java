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
        require(images==74,"PNG数: "+images);require(json>=170,"JSON数: "+json);
        System.out.println("NetworkAssetValidator: "+json+" JSON / "+images+" PNG / all references resolved");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
