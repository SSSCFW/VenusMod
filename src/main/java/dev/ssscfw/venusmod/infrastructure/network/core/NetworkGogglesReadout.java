package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.ArrayList;
import java.util.List;

/** ゴーグル表示用の読み取り専用データ。接続状態はシミュレータの論理リンクを正とする。 */
public final class NetworkGogglesReadout {
    public record Port(boolean visible, boolean connected, boolean enabled, boolean linkUp,
                       String cidr, long maximumBps, int vlan, boolean trunk) {
        public Port {
            if (cidr == null || cidr.length() > 24 || maximumBps < 0
                    || vlan < 1 || vlan > 4094) {
                throw new IllegalArgumentException("不正なポート表示");
            }
        }
    }
    private NetworkGogglesReadout() {}

    public static List<Port> empty() {
        List<Port> ports = new ArrayList<>(NetworkProfile.PORT_COUNT);
        for (int i = 0; i < NetworkProfile.PORT_COUNT; i++) {
            ports.add(new Port(false, false, true, false, "", 0, 1, false));
        }
        return List.copyOf(ports);
    }

    public static List<Port> inspect(NetworkSimulator simulator, String id) {
        NetworkProfile profile = simulator.nodes().get(id);
        if (profile == null) return empty();
        List<Port> ports = new ArrayList<>(NetworkProfile.PORT_COUNT);
        for (int i = 0; i < NetworkProfile.PORT_COUNT; i++) {
            NetworkProfile.Port local = profile.ports[i];
            NetworkSimulator.Link link = simulator.linkAt(id, i);
            boolean connected = link != null;
            boolean visible = connected || !local.address.isBlank();
            String ip = local.address;
            int prefix = local.prefix;
            // 非ルーターのIPv4は機器全体の設定。未使用ポートには割り当てない。
            if (ip.isBlank() && connected && !profile.kind.routing()) {
                ip = profile.address;
                prefix = profile.prefix;
            }
            String cidr = ip.isBlank() ? "" : ip + "/" + prefix;
            long actual = connected ? simulator.linkCapacity(link) : 0;
            long maximum = local.capacity();
            if (connected) {
                maximum = Math.min(maximum, link.grade().bps());
                NetworkProfile remote = simulator.nodes().get(link.peer(id));
                if (remote != null) {
                    maximum = Math.min(maximum, remote.ports[link.peerPort(id)].capacity());
                    if (link.wan()) {
                        maximum = Math.min(maximum,
                                NetworkProfile.WAN_SPEEDS[Math.clamp(profile.wanTier, 0, 5)]);
                        maximum = Math.min(maximum,
                                NetworkProfile.WAN_SPEEDS[Math.clamp(remote.wanTier, 0, 5)]);
                    }
                }
                if (actual > 0) maximum = actual;
            }
            ports.add(new Port(visible, connected, local.enabled, actual > 0, cidr,
                    maximum, local.vlan, local.trunk));
        }
        return List.copyOf(ports);
    }
}
