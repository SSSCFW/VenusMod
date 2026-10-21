import dev.ssscfw.venusmod.infrastructure.network.core.*;
import static dev.ssscfw.venusmod.infrastructure.network.core.NetworkDiagnostics.Action.*;

public final class NetworkDiagnosticsTest {
    private static int checks;
    private static void equal(String expected,String actual){checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    private static void reject(Runnable action){checks++;try{action.run();throw new AssertionError("Accepted invalid diagnostics input");}catch(IllegalArgumentException expected){}}
    private static String command(NetworkDiagnostics.Action a,String target,String size,TransferPriority priority){return NetworkDiagnostics.command(a,target,size,priority);}
    public static void main(String[] args){
        equal("ping 192.168.1.20",command(PING," 192.168.1.20 ","bad",null));
        equal("trace base.local",command(TRACE,"BASE.Local.",null,null));
        equal("nslookup base.local",command(DNS,"BASE.Local.",null,null));
        equal("transfer base.local:8080 100 high",command(TRANSFER,"BASE.Local:08080","00100",TransferPriority.HIGH));
        equal("transfer 192.168.1.20:5000 1 low",command(TRANSFER,"192.168.1.20","1",TransferPriority.LOW));
        equal("transfer base.local:5000 1000000 normal",command(TRANSFER,"base.local","1000000",TransferPriority.NORMAL));
        equal("flows",command(FLOWS,"invalid\ninput","invalid",null));
        for(var action:new NetworkDiagnostics.Action[]{PING,TRACE,DNS,TRANSFER}){
            for(String invalid:new String[]{""," ","192.168.1.999","http://base.local","base.local show","base.local\nshow","base.local\t","base.local;fault","base/local","x".repeat(90),"::1","base.local\u0000"})
                reject(()->command(action,invalid,"100",TransferPriority.NORMAL));
        }
        for(var action:new NetworkDiagnostics.Action[]{PING,TRACE,DNS})reject(()->command(action,"base.local:80","100",TransferPriority.NORMAL));
        reject(()->command(DNS,"192.168.1.20","100",TransferPriority.NORMAL));
        for(String bad:new String[]{"base.local:0","base.local:65536","base.local:abc","base.local:","base.local:80:90"})reject(()->command(TRANSFER,bad,"100",TransferPriority.NORMAL));
        for(String bad:new String[]{"0","1000001","-1","+1","1.5","1e3","1 2","\n1","999999999999999999999999"})reject(()->command(TRANSFER,"base.local",bad,TransferPriority.NORMAL));
        reject(()->command(TRANSFER,"base.local","100",null));
        reject(()->command(null,"base.local","100",TransferPriority.NORMAL));
        equal("ping cancel",command(PING,"cancel","0",null));
        equal("transfer priority:5000 100 normal",command(TRANSFER,"priority","100",TransferPriority.NORMAL));
        // 生成したコマンドを実際のCLIへ渡し、予約語名を管理サブコマンドにしない。
        var sim=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="10.166.0.10";a.dns="10.166.0.20";
        var b=new NetworkProfile(DeviceKind.SERVER);b.address="10.166.0.20";b.name="diagnostic-server";
        for(String name:new String[]{"priority","cancel","help"})b.dnsRecords.put(name,b.address);
        sim.put("a",a);sim.put("b",b);sim.link(new NetworkSimulator.Link("wire","a",1,"b",3,CableGrade.CAT5E,2,false));
        String profile=a.encode();
        for(var action:new NetworkDiagnostics.Action[]{PING,TRACE}){
            String result=String.join("\n",NetworkCli.run(sim,"a",command(action,b.address,"bad",null)).lines());
            checks++;if(!result.contains("Reply from"))throw new AssertionError(result);
        }
        for(String name:new String[]{"priority","cancel","help"}){
            String result=String.join("\n",NetworkCli.run(sim,"a",command(TRANSFER,name,"1000000",TransferPriority.NORMAL)).lines());
            checks++;if(!result.contains("開始"))throw new AssertionError(result);
        }
        checks++;if(sim.flows().size()!=3)throw new AssertionError("Reserved hosts did not start three transfers");
        equal(profile,a.encode());
        String dns=String.join("\n",NetworkServiceCommands.run(sim,"a",command(DNS,"priority","",null)).lines());
        checks++;if(!dns.contains("10.166.0.20"))throw new AssertionError(dns);
        sim.unlink("wire");
        String down=String.join("\n",NetworkCli.run(sim,"a",command(PING,b.address,"",null)).lines());
        checks++;if(down.contains("Reply from"))throw new AssertionError("Broken cable accepted");
        checks++;if(sim.flows().size()!=0)throw new AssertionError("Disconnected flows must stop");
        var guide=NetworkFormGuide.page().steps().toString();
        for(String word:new String[]{"診断","転送開始","名前解決","未送信"}){
            checks++;if(!guide.contains(word))throw new AssertionError("Missing common guide: "+word);
        }
        System.out.println("NetworkDiagnosticsTest: "+checks+" checks passed");
    }
}
