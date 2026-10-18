import dev.ssscfw.venusmod.infrastructure.network.core.*;

public final class ControlSafetyGuideTest {
    public static void main(String[] args){
        var p=NetworkGuide.pages().stream().filter(x->x.id().equals("control-safety")).findFirst().orElseThrow();
        if(p.steps().size()!=5||p.nodes().size()!=3)throw new AssertionError("安全動作の接続図と5手順");
        var text=p.toString()+NetworkHelp.lines();
        for(String required:new String[]{"control-safety stop", "value", "hold", "maintain", "control-status", "redstone stop", "監視", "所有者", "再配置", "自動再開"})
            if(!text.contains(required))throw new AssertionError(required);
        System.out.println("ControlSafetyGuideTest: 12 checks passed");
    }
}
