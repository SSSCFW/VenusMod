import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class NtpRuntimeTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void near(double a,double b,String why){check(Math.abs(a-b)<0.02,why+": "+a+" != "+b);}
    static void command(NetworkSimulator s,String id,String command){var r=NetworkServiceCommands.run(s,id,command);check(r.handled()&&r.changed(),command+": "+r.lines());}
    static NetworkProfile host(DeviceKind kind,String address){var p=new NetworkProfile(kind);p.address=address;return p;}
    static NetworkSimulator lan(){var s=new NetworkSimulator();s.put("a",host(DeviceKind.PC,"192.0.2.1"));s.put("b",host(DeviceKind.SERVER,"192.0.2.2"));s.link(new NetworkSimulator.Link("ab","a",0,"b",0,CableGrade.CAT5E,2,false));return s;}
    public static void main(String[] args){
        var sample=NtpSample.calculate(1000,6010,6010,1020);near(sample.offsetMillis(),5000,"four timestamps offset");near(sample.delayMillis(),20,"four timestamps delay");
        near(NtpSample.calculate(6000,1010,1010,6020).offsetMillis(),-5000,"negative offset");
        near(NtpSample.calculate(1000,6005,6005,1025).offsetMillis(),4992.5,"asymmetric path bias is not magically removed");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){boolean rejected=false;try{NtpSample.calculate(bad,0,0,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"nonfinite timestamps");}
        boolean bad=false;try{NtpSample.calculate(2,2,1,1);}catch(IllegalArgumentException e){bad=true;}check(bad,"reversed interval");
        var s=lan();command(s,"b","ntp server on");command(s,"a","ntp offset 5000");command(s,"a","ntp target 192.0.2.2");command(s,"a","ntp interval 1");command(s,"a","ntp on");
        check(s.ntp().snapshot("a").state().equals("WAITING"),"not synced just by enabling");near(s.ntp().clockMillis("a")-s.ntp().clockMillis("b"),5000,"clock starts with configured error");
        s.advance(1);check(s.ntp().snapshot("a").state().equals("SYNCED"),"periodic sync");near(s.ntp().clockMillis("a"),s.ntp().clockMillis("b"),"clocks aligned");check(s.ntp().snapshot("a").samples()==1,"one sample");
        long age=s.ntp().snapshot("a").lastSuccess();command(s,"a","ntp on");check(s.ntp().snapshot("a").lastSuccess()==age,"same settings no reset");
        s.startTransfer("a","192.0.2.2",1_000_000_000L);int flows=s.flows().size();command(s,"a","ntp offset -2000");check(s.flows().size()==flows,"clock edit preserves transfer");
        long monitorTime=s.monitor().now(),natTime=s.nat().time();s.advance(1);check(s.monitor().now()==monitorTime+1&&s.nat().time()==natTime+1,"only real simulator ticks advance timer runtimes");
        near(s.ntp().clockMillis("a"),s.ntp().clockMillis("b"),"negative clock correction");
        command(s,"b","ntp server off");s.advance(20);check(s.ntp().snapshot("a").state().equals("HOLDOVER"),"failure preserves last corrected clock");near(s.ntp().clockMillis("a"),s.ntp().clockMillis("b"),"failed sync doesn't step clock");
        s.advance(60);check(s.ntp().snapshot("a").state().equals("STALE"),"old success no longer advertised as fresh");
        command(s,"b","ntp server on");s.advance(20);check(s.ntp().snapshot("a").state().equals("SYNCED"),"resync after recovery");
        s.nodes().get("a").failed=true;s.advance(20);check(s.ntp().snapshot("a").state().equals("PAUSED"),"failed client paused");s.nodes().get("a").failed=false;s.advance(20);check(s.ntp().snapshot("a").state().equals("SYNCED"),"repair resumes sync");
        command(s,"a","ntp drift 1000");s.advance(1);double start=s.ntp().clockMillis("a")-s.ntp().clockMillis("b");command(s,"a","ntp off");s.advance(20);near(s.ntp().clockMillis("a")-s.ntp().clockMillis("b")-start,1,"free running 1000ppm drifts 1ms per second");
        check(s.ntp().scheduled()==0,"off removes queued deadline");
        var before=s.nodes().get("a").encode();var invalid=NetworkServiceCommands.run(s,"a","ntp offset 86400001");check(!invalid.changed()&&before.equals(s.nodes().get("a").encode()),"invalid input is atomic");
        check(!NetworkServiceCommands.run(s,"a","ntp server on").changed(),"PC cannot serve reference time");command(s,"b","ntp target 192.0.2.1");check(!NetworkServiceCommands.run(s,"b","ntp on").changed(),"reference server cannot also follow client source");
        s.remove("a");check(s.ntp().clockCount()==1,"removal releases runtime state");
        System.out.println("NtpRuntimeTest: "+checks+" checks passed");
    }
}
