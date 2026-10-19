import dev.ssscfw.venusmod.infrastructure.network.core.*;
public final class NtpContractTest {
    public static void main(String[] args){
        var sim=new NetworkSimulator();sim.put("server",new NetworkProfile(DeviceKind.SERVER));
        var reply=NetworkServiceCommands.run(sim,"server","ntp server on");
        if(!reply.handled()||!reply.changed())throw new AssertionError("ntp server on must enable the virtual time service: "+reply);
        System.out.println("NtpContractTest: passed");
    }
}
