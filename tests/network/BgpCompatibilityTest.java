import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** 新BGPを使わないネットワークの既存診断も変更しない。 */
public final class BgpCompatibilityTest {
    public static void main(String[] args){
        var sim=new NetworkSimulator();var r=BgpContractTest.router();r.ports[0].address="10.0.0.1";r.ports[1].address="10.0.0.2";
        sim.put("r",r);sim.put("x",BgpContractTest.host("10.0.0.10","10.0.0.1"));sim.put("y",BgpContractTest.host("10.0.0.20","10.0.0.2"));
        BgpContractTest.link(sim,"rx","r",0,"x",0);BgpContractTest.link(sim,"ry","r",1,"y",0);
        if(!sim.route("r","10.0.0.20").success())throw new AssertionError("同じ長さの直結候補を1ポートに切り詰めない (一方向診断)");
        System.out.println("BgpCompatibilityTest: existing connected candidate enumeration passed");
    }
}
