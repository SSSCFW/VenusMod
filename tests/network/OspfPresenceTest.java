/** 新機能未実装時にもコンパイルできる公開契約テスト。 */
public final class OspfPresenceTest {
    public static void main(String[] args) {
        try {
            Class<?> settings=Class.forName("dev.ssscfw.venusmod.infrastructure.network.core.OspfSettings");
            Object value=settings.getConstructor().newInstance();
            if(settings.getField("enabled").getBoolean(value))throw new AssertionError("既存機器のOSPFは勝手に有効にしない");
            Class.forName("dev.ssscfw.venusmod.infrastructure.network.core.OspfEngine").getMethod("advance",long.class);
        } catch (ReflectiveOperationException missing) { throw new AssertionError("OSPF設定とタイマー駆動エンジンが未実装",missing); }
        System.out.println("OspfPresenceTest: default disabled and scheduled engine present");
    }
}
