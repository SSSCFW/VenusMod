import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class ConfigBackupTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void rejects(Runnable action,String why){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected,why);}
    public static void main(String[] args){
        var p=new NetworkProfile(DeviceKind.ROUTER);p.name="保存前ルーター";p.ports[0].address="10.20.1.1";p.failed=true;p.publicService=true;p.ports[0].moduleTier=4;p.processor=6;
        p.routes.add(new NetworkProfile.Route("10.30.0.0",16,"10.20.1.2",0));
        var snap=ConfigBackup.capture("router-a",p,10);
        p.name="changed";p.routes.clear();p.failed=false;p.publicService=false;p.ports[0].moduleTier=1;p.processor=2;p.wanTier=1;
        var restored=snap.restored(p);
        ok(restored.name.equals("保存前ルーター")&&restored.routes.size()==1,"immutable configuration snapshot");
        ok(!restored.failed&&!restored.publicService,"no fault rollback or reopened public service");
        ok(restored.ports[0].moduleTier==1&&restored.processor==2&&restored.wanTier==1,"hardware comes from current modules");
        ok(p.name.equals("changed")&&p.routes.isEmpty(),"preview does not mutate current settings");
        p.failed=true;ok(snap.restored(p).failed,"restoring healthy snapshot cannot repair current fault");
        rejects(()->snap.restored(new NetworkProfile(DeviceKind.PC)),"wrong model rejected");
        rejects(()->new ConfigBackup(snap.kind(),snap.sourceId(),snap.savedTick(),snap.configuration(),"0".repeat(64)),"bad checksum");
        rejects(()->ConfigBackup.capture("../bad",p,10),"path-like source rejected");
        var fast=new NetworkProfile(DeviceKind.PC);fast.ports[0].moduleTier=4;fast.ports[0].speedLimitBps=400_000_000_000L;
        var fastSnap=ConfigBackup.capture("fast",fast,0);var slow=new NetworkProfile(DeviceKind.PC);
        rejects(()->fastSnap.restored(slow),"missing fast module cannot grant speed");slow.ports[0].moduleTier=4;ok(fastSnap.restored(slow).ports[0].speedLimitBps==400_000_000_000L,"installed module permits restore");
        fast.ports[0].speedLimitBps=0;fast.ports[0].optical=true;var optical=ConfigBackup.capture("optical",fast,0);
        rejects(()->optical.restored(new NetworkProfile(DeviceKind.PC)),"optical hardware required");
        var dhcp=new NetworkProfile(DeviceKind.SERVER);dhcp.address="10.4.0.1";
        var pool=new NetworkServices.Pool(NetworkPolicy.Prefix.parse("10.4.0.0/24"),"10.4.0.100","10.4.0.110","10.4.0.1","");dhcp.services.addPool(pool);dhcp.services.reserve("client-old",pool,"10.4.0.100");
        var dhcpSnap=ConfigBackup.capture("dhcp",dhcp,0);
        ok(NetworkProfile.decode(DeviceKind.SERVER,dhcpSnap.configuration()).services.bindings().isEmpty(),"old allocation reservations not backed up");
        dhcp.services.clearBindings();dhcp.services.reserve("client-new",pool,"10.4.0.101");
        var merged=dhcpSnap.restored(dhcp);ok(merged.services.bindings().equals(dhcp.services.bindings()),"current DHCP reservations survive rollback");
        dhcp.services.clearPools();var other=new NetworkServices.Pool(NetworkPolicy.Prefix.parse("10.5.0.0/24"),"10.5.0.100","10.5.0.110","10.5.0.1","");dhcp.services.addPool(other);dhcp.services.reserve("other",other,"10.5.0.100");
        rejects(()->dhcpSnap.restored(dhcp),"changing occupied pools requires explicit reservation handling");
        var archive=new BackupArchive();ok(!archive.serving(),"remote archive disabled by default");archive.save("first",snap);archive.serving(true);
        var loaded=BackupArchive.decode(archive.encode());ok(loaded.serving()&&loaded.get("first").equals(snap),"archive persistence including service setting");
        rejects(()->archive.save("first",snap),"no silent overwrite");
        rejects(()->archive.save("../../world",snap),"no path syntax");
        rejects(()->BackupArchive.decode("A".repeat(BackupArchive.MAX_ENCODED+1)),"encoded archive capacity bound");
        String encoded=archive.encode();rejects(()->BackupArchive.decode(encoded.substring(0,encoded.length()-4)),"truncated archive rejected");
        rejects(()->BackupArchive.decode(encoded+"AAAA"),"extra bytes rejected");
        for(int i=1;i<BackupArchive.MAX_ENTRIES;i++)archive.save("p"+i,snap);
        rejects(()->archive.save("overflow",snap),"entry limit");ok(archive.get("first").equals(snap),"limit does not evict oldest backup");
        archive.remove("first");archive.save("replacement",snap);ok(archive.entries().size()==BackupArchive.MAX_ENTRIES,"explicit deletion releases capacity");
        boolean immutable=false;try{archive.entries().clear();}catch(UnsupportedOperationException e){immutable=true;}ok(immutable,"archive list does not expose mutable storage");
        var large=new NetworkProfile(DeviceKind.ROUTER);// bulk content stays within Profile's existing bound
        for(int i=0;i<64;i++)large.routes.add(new NetworkProfile.Route("10."+i+".0.0",16,"192.168.1.2",0));
        for(int i=0;i<64;i++)large.dnsRecords.put("x".repeat(57)+String.format("%02d",i),"192.168.1.20");
        var big=ConfigBackup.capture("large",large,10);var filled=new BackupArchive();int count=0;while(count<BackupArchive.MAX_ENTRIES){try{filled.save("b"+count,big);count++;}catch(IllegalArgumentException full){break;}}
        ok(count>0&&filled.textSize()<=BackupArchive.MAX_TOTAL_TEXT,"total profile text bounded");ok(count<BackupArchive.MAX_ENTRIES,"text limit independently enforced before count limit");
        ok(BackupArchive.decode(filled.encode()).entries().size()==count,"large valid archive decodes");
        System.out.println("ConfigBackupTest: "+checks+" checks passed");
    }
}
