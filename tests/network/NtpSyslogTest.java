import dev.ssscfw.venusmod.infrastructure.network.core.*;
public final class NtpSyslogTest {
    static int checks;
    static void ok(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    static void set(NetworkSimulator s,String id,String cmd){var r=NetworkServiceCommands.run(s,id,cmd);if(!r.changed())throw new AssertionError(r.lines());}
    public static void main(String[] args){
        var s=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="192.0.2.1";var b=new NetworkProfile(DeviceKind.SERVER);b.address="192.0.2.2";
        s.put("a",a);s.put("b",b);s.link(new NetworkSimulator.Link("ab","a",0,"b",0,CableGrade.CAT5E,2,false));
        set(s,"a","ntp offset 5000");set(s,"a","syslog target 192.0.2.2");set(s,"a","syslog on");set(s,"b","syslog server on");
        s.log("a",3,"before-sync");s.advance(1);var event=s.syslog().entries("b").getFirst();
        ok(event.emittedClockMillis()==5000&&event.receivedClockMillis()==50,"Syslog records distinct sender and collector clocks");
        long sequence=event.number();double timestamp=event.emittedClockMillis();set(s,"b","ntp server on");set(s,"a","ntp target 192.0.2.2");set(s,"a","ntp on");s.advance(1);
        ok(s.syslog().entries("b").getFirst().emittedClockMillis()==timestamp,"sync never rewrites old received history");
        s.log("a",3,"after-sync");s.advance(3);var after=s.syslog().entries("b").stream().filter(e->e.message().equals("after-sync")).findFirst().orElseThrow();
        ok(after.emittedClockMillis()<timestamp&&after.number()>sequence,"backward clock correction preserves monotonic receipt ordering");
        s.log("a",3,"queued-before-edit");double queued=s.ntp().clockMillis("a");set(s,"a","ntp offset -5000");s.advance(1);
        var stored=s.syslog().entries("b").stream().filter(e->e.message().equals("queued-before-edit")).findFirst().orElseThrow();
        ok(stored.emittedClockMillis()==queued,"queued message uses emit-time clock not delivery-time recomputation");
        ok(s.syslog().describe("b",12).stream().anyMatch(line->line.contains("clockMs=")),"CLI renders immutable clock pair as well as ticks");
        System.out.println("NtpSyslogTest: "+checks+" checks passed");
    }
}
