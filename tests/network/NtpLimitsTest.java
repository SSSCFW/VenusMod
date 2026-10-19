import dev.ssscfw.venusmod.infrastructure.network.core.*;
public final class NtpLimitsTest {
    static int checks;
    static void ok(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    public static void main(String[] args){
        var s=new NetworkSimulator();
        for(int i=0;i<NtpRuntime.MAX_CLIENTS;i++){
            var p=new NetworkProfile(DeviceKind.PC);p.address="10.0.0.1";p.ntp.enabled=true;p.ntp.target="10.0.0.2";p.ntp.intervalSeconds=1;s.put("c"+i,p);
        }
        ok(s.ntp().scheduled()==256,"one deadline per active client");
        var p=new NetworkProfile(DeviceKind.PC);p.address="10.0.0.1";p.ntp.target="10.0.0.2";s.put("extra",p);
        String old=p.encode();var denied=NetworkServiceCommands.run(s,"extra","ntp on");ok(!denied.changed()&&old.equals(s.nodes().get("extra").encode()),"limit refuses new setting atomically");
        s.advance(1_000_000);ok(s.ntp().lastWork()==2&&s.ntp().totalAttempts()==2&&s.ntp().scheduled()==256,"time jump does not execute missed intervals");
        for(int i=0;i<127;i++)s.advance(1);
        ok(s.ntp().totalAttempts()==256&&s.ntp().scheduled()==256,"fair queue visits each overdue client before repeated ones");
        s.remove("c0");ok(NetworkServiceCommands.run(s,"extra","ntp on").changed(),"freed slot can be reused");
        for(int i=0;i<20;i++)NetworkServiceCommands.run(s,"extra","ntp sync");ok(s.ntp().scheduled()==256,"manual sync replaces its own deadline instead of bypassing limit");
        var unloaded=new NetworkProfile(DeviceKind.PC);unloaded.address="10.0.0.1";unloaded.ntp.enabled=true;unloaded.ntp.target="10.0.0.2";s.put("loaded",unloaded);
        ok(s.ntp().snapshot("loaded").state().equals("LIMIT")&&unloaded.ntp.enabled,"loading above limit preserves config but pauses that client");
        s.remove("extra");ok(NetworkServiceCommands.run(s,"loaded","ntp on").changed()&&s.ntp().scheduled()==256&&!s.ntp().snapshot("loaded").state().equals("LIMIT"),"same configuration can retry capacity-limited loaded device");
        s.nodes().get("loaded").failed=true;s.advance(1_000_000_000L);ok(s.ntp().lastWork()<=2,"large elapsed time remains bounded");
        ok(Double.isFinite(s.ntp().clockMillis("loaded")),"large monotonic time does not create nonfinite clock value");
        System.out.println("NtpLimitsTest: "+checks+" checks passed");
    }
}
