import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** サーバー保管庫の認可/容量判断もNAT取引内で行う。 */
public final class BackupTransportTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void config(NetworkSimulator sim,String id,String line){var r=NetworkServiceCommands.run(sim,id,line);if(!r.changed())throw new AssertionError(line+" "+r.lines());}
    public static void main(String[] args){
        var s=new NetworkSimulator();var client=new NetworkProfile(DeviceKind.PC);client.address="10.90.0.10";client.gateway="10.90.0.1";
        var router=new NetworkProfile(DeviceKind.ROUTER);router.ports[0].address="10.90.0.1";router.ports[1].address="198.51.100.1";
        var server=new NetworkProfile(DeviceKind.SERVER);server.address="198.51.100.20";
        s.put("c",client);s.put("r",router);s.put("s",server);s.link(new NetworkSimulator.Link("cr","c",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("rs","r",1,"s",0,CableGrade.CAT5E,2,false));
        var ep=new ServiceAddress(server.address,5001);
        ok(!s.backupRequest("c",ep,id->"").success(),"requires return route");
        config(s,"r","nat port 0 inside");config(s,"r","nat port 1 outside");config(s,"r","nat on");
        ok(!s.backupRequest("c",ep,id->"権限なし").success()&&s.nat().sessions("r").isEmpty(),"denial before NAT commit");
        var yes=s.backupRequest("c",ep,id->id.equals("s")?"":"wrong device");ok(yes.success()&&yes.destination().equals("s"),"real destination identity checked");
        long expiry=s.nat().sessions("r").getFirst().expiresAt();s.advance(5);
        ok(!s.backupRequest("c",ep,id->"保管庫満杯").success()&&s.nat().sessions("r").getFirst().expiresAt()==expiry,"capacity denial does not renew NAT");
        ok(!s.backupRequest("c",new ServiceAddress(server.address,5000),id->"").success(),"dedicated port validated");
        config(s,"r","acl add 0 out 10 deny tcp any any");
        ok(!s.backupRequest("c",ep,id->"").success()&&s.nat().sessions("r").isEmpty(),"reverse ACL rejects transaction");config(s,"r","acl clear 0 out");
        var wrong=new NetworkProfile(DeviceKind.PC);wrong.address=server.address;s.put("s",wrong);
        ok(!s.backupRequest("c",ep,id->"").success(),"a PC does not become backup server");s.put("s",server);
        s.unlink("rs");ok(!s.backupRequest("c",ep,id->"").success(),"broken cable prevents remote backup");
        var outside=new NetworkSimulator();var ext=new NetworkProfile(DeviceKind.PC);ext.address="203.0.113.10";var nr=new NetworkProfile(DeviceKind.ROUTER);nr.ports[0].address="203.0.113.1";nr.ports[1].address="10.91.0.1";
        var inner=new NetworkProfile(DeviceKind.SERVER);inner.address="10.91.0.20";inner.gateway="10.91.0.1";
        outside.put("e",ext);outside.put("r",nr);outside.put("i",inner);outside.link(new NetworkSimulator.Link("er","e",0,"r",0,CableGrade.CAT5E,1,false));outside.link(new NetworkSimulator.Link("ri","r",1,"i",0,CableGrade.CAT5E,1,false));
        config(outside,"r","nat port 0 outside");config(outside,"r","nat port 1 inside");config(outside,"r","nat on");config(outside,"r","nat forward add 0 tcp 9001 10.91.0.20 5001");
        ok(outside.backupRequest("e",new ServiceAddress("203.0.113.1",9001),id->id.equals("i")?"":"wrong").success(),"public port maps to private backup service");
        ok(!outside.backupRequest("e",new ServiceAddress("203.0.113.1",9001),id->"サービス停止").success(),"remote service disabled");
        config(outside,"r","nat forward delete 0 tcp 9001");config(outside,"r","nat forward add 0 tcp 9001 10.91.0.20 80");
        ok(!outside.backupRequest("e",new ServiceAddress("203.0.113.1",9001),id->"").success(),"NAT wrong inner port rejected");
        System.out.println("BackupTransportTest: "+checks+" checks passed");
    }
}
