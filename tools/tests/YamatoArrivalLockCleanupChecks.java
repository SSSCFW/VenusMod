import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/** 消滅した対象だけを全ての到着端から除去し、他の対象のロックを保つ。 */
public final class YamatoArrivalLockCleanupChecks {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("dev.ssscfw.venusmod.yamato.YamatoRiftRules$ArrivalLocks");
        Method remove;
        try {
            remove = type.getMethod("removeEntity", UUID.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Destroyed arrivals cannot be removed from all endpoint locks", missing);
        }
        Object locks = type.getConstructor().newInstance();
        Method arrived = type.getMethod("arrived", UUID.class, UUID.class);
        Method blocked = type.getMethod("blocked", UUID.class, UUID.class);
        Method entities = type.getMethod("entities", UUID.class);
        UUID destroyed = UUID.randomUUID(), survivor = UUID.randomUUID();
        UUID first = UUID.randomUUID(), overlapping = UUID.randomUUID(), unrelated = UUID.randomUUID();
        arrived.invoke(locks, destroyed, first);
        arrived.invoke(locks, destroyed, overlapping);
        arrived.invoke(locks, survivor, first);
        arrived.invoke(locks, survivor, unrelated);

        check((boolean)remove.invoke(locks, destroyed), "Removing a known arrival must report a saved-state change");
        check(!(boolean)blocked.invoke(locks, destroyed, first), "Destroyed arrival remains at its original endpoint");
        check(!(boolean)blocked.invoke(locks, destroyed, overlapping), "Destroyed arrival remains at an overlapping endpoint");
        check(((Set<?>)entities.invoke(locks, overlapping)).isEmpty(), "Empty endpoint still contains a destroyed arrival");
        check((boolean)blocked.invoke(locks, survivor, first), "Cleanup removed another entity's arrival lock");
        check((boolean)blocked.invoke(locks, survivor, unrelated), "Cleanup changed an unrelated endpoint");
        check(!(boolean)remove.invoke(locks, destroyed), "Repeated cleanup must not report another state change");
        check(!(boolean)remove.invoke(locks, UUID.randomUUID()), "Unknown entities must not change saved locks");
        System.out.println("YamatoArrivalLockCleanupChecks: destroyed arrivals removed, overlapping and surviving locks verified");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
