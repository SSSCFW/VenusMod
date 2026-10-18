import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** ワイヤ接続・未使用IP・VLANの表示条件に関する純Java回帰テスト。 */
public final class NetworkGogglesReadoutTest {
    private static int checks;
    private static void require(boolean good, String detail) {
        checks++;
        if (!good) throw new AssertionError(detail);
    }
    public static void main(String[] args) {
        NetworkSimulator sim = new NetworkSimulator();
        NetworkProfile sw = new NetworkProfile(DeviceKind.L3_SWITCH);
        sw.ports[0].address = "10.0.0.1";
        sw.ports[0].prefix = 25;
        sw.ports[2].trunk = true;
        sw.ports[2].vlan = 32;
        sim.put("switch", sw);
        NetworkProfile peer = new NetworkProfile(DeviceKind.ROUTER);
        sim.put("router", peer);
        sim.link(new NetworkSimulator.Link("cable", "switch", 2, "router", 2,
                CableGrade.CAT6A, 3, false));
        var ports = NetworkGogglesReadout.inspect(sim, "switch");
        require(ports.size() == 6, "six ports");
        require(ports.get(0).visible() && !ports.get(0).connected()
                && ports.get(0).cidr().equals("10.0.0.1/25"), "unused configured CIDR");
        require(ports.get(1).visible() && !ports.get(1).connected()
                && ports.get(1).cidr().equals("192.168.2.1/24"), "initial port IP");
        require(ports.get(2).visible() && ports.get(2).connected()
                && ports.get(2).trunk() && ports.get(2).vlan() == 32, "trunk VLAN");
        require(ports.get(2).linkUp() && ports.get(2).maximumBps() == 1_000_000_000L,
                "effective physical bandwidth");
        require(!ports.get(3).visible() && !ports.get(4).visible()
                && !ports.get(5).visible(), "unconfigured unused port hidden");
        sw.failed = true;
        ports = NetworkGogglesReadout.inspect(sim, "switch");
        require(!ports.get(2).linkUp() && ports.get(2).maximumBps() > 0,
                "link down when chassis fails");
        NetworkProfile pc = new NetworkProfile(DeviceKind.PC);
        pc.address = "192.168.9.5";
        pc.prefix = 27;
        sim.put("pc", pc);
        ports = NetworkGogglesReadout.inspect(sim, "pc");
        require(ports.stream().noneMatch(NetworkGogglesReadout.Port::visible),
                "global PC address must not illuminate unused ports");
        sim.link(new NetworkSimulator.Link("pc-link", "pc", 1, "router", 1,
                CableGrade.CAT5E, 2, false));
        ports = NetworkGogglesReadout.inspect(sim, "pc");
        require(ports.get(1).visible() && ports.get(1).cidr().equals("192.168.9.5/27"),
                "connected PC interface inherits global CIDR");
        require(NetworkGogglesReadout.empty().size() == 6, "denied snapshot shape");
        System.out.println("NetworkGogglesReadoutTest: " + checks + " checks");
    }
}
