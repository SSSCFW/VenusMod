import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** ランダム移動の距離境界とGUI・サーバーの配線をMinecraftなしで検証する。 */
public final class YamatoRandomSettingsChecks {
    private static int checks;
    private static void check(boolean pass, String message) {
        checks++; if (!pass) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Class<?> rules=Class.forName("dev.ssscfw.venusmod.yamato.YamatoTravelRules");
        Method valid=rules.getMethod("validRandomSettings",int.class,int.class);
        for(int n:new int[]{1,2,50,1000,123456,1000000})for(int mode=0;mode<=1;mode++)
            check((boolean)valid.invoke(null,n,mode),"Valid settings rejected "+n+"/"+mode);
        for(int n:new int[]{-1,0,1000001,Integer.MAX_VALUE})for(int mode=0;mode<=1;mode++)
            check(!(boolean)valid.invoke(null,n,mode),"Invalid radius accepted "+n+"/"+mode);
        for(int mode:new int[]{-1,2,3,Integer.MAX_VALUE})
            check(!(boolean)valid.invoke(null,1000,mode),"Invalid mode accepted "+mode);
        String root="src/main/java/dev/ssscfw/venusmod/";
        String screen=Files.readString(Path.of(root+"client/YamatoTravelScreen.java"));
        String network=Files.readString(Path.of(root+"yamato/YamatoNetwork.java"));
        String rifts=Files.readString(Path.of(root+"yamato/YamatoRifts.java"));
        String data=Files.readString(Path.of(root+"yamato/YamatoTravelData.java"));
        check(screen.contains("new RandomTravelScreen(this)"),"Random button not connected");
        check(screen.contains("request(\"random_travel\", command)"),"GUI does not send random travel");
        check(screen.contains("request(\"random_settings\", data)"),"Options do not autosave");
        check(screen.contains("setMaxLength(7)"),"Cannot input 1,000,000");
        check(network.contains("case \"random_travel\""),"Server has no random travel handler");
        check(network.contains("applyTravelSettings(player, request)"),"Travel loses close conditions");
        check(rifts.contains("MAX_RANDOM_ATTEMPTS = 48"),"Unbounded random search");
        String random=Files.readString(Path.of(root+"yamato/YamatoRandomDestinations.java"));
        check(random.contains("level.getChunk(cx, cz)"),"Distant candidate chunks are not explicitly loaded");
        check(random.contains("findNearby(ServerLevel level"),"Nearby dry landing search not available");
        check(random.contains("withinRange(origin, range, x, z)"),"Nearby fallback can exceed selected radius");
        check(rifts.contains("target.isPositionEntityTicking(BlockPos.containing(to.feet))"),
                "Far rift attempts entity teleport before destination is ticking");
        check(rifts.contains("YamatoRandomDestinations.safeLanding(target"),"No destination recheck");
        check(data.contains("result.put(\"randomSettings\", randomSettings(owner))"),"Options not persisted");
        System.out.println("YamatoRandomSettingsChecks: "+checks+" passed");
    }
}
