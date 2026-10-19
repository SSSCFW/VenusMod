import dev.ssscfw.venusmod.infrastructure.network.core.*;

public final class BackupRestoreTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void rejects(Runnable op,String why){boolean fail=false;try{op.run();}catch(IllegalArgumentException e){fail=true;}ok(fail,why);}
    public static void main(String[] args){
        var current=new NetworkProfile(DeviceKind.PC);current.address="10.2.0.10";var store=new BackupArchive();store.save("good",ConfigBackup.capture("pc",current,0));current.address="10.2.0.11";
        var pending=new BackupRestore();var preview=pending.prepare(store,"good",current,20);String token=pending.token();
        ok(preview.contains("10.2.0.10")&&!preview.contains(store.get("good").configuration()),"preview only displays summary not encoded configuration");
        ok(current.address.equals("10.2.0.11"),"preparation has no side effects");
        var done=pending.confirm(store,token,current,21);ok(done.address.equals("10.2.0.10"),"confirmed restore");rejects(()->pending.confirm(store,token,current,22),"confirmation single-use");
        pending.prepare(store,"good",current,30);String stale=pending.token();current.name="changed";rejects(()->pending.confirm(store,stale,current,31),"concurrent configuration update");
        pending.prepare(store,"good",current,40);String hardware=pending.token();current.ports[0].moduleTier=1;rejects(()->pending.confirm(store,hardware,current,41),"module changed while awaiting confirmation");
        pending.prepare(store,"good",current,50);String replaced=pending.token();store.remove("good");store.save("good",ConfigBackup.capture("pc",current,51));rejects(()->pending.confirm(store,replaced,current,52),"replaced backup entry");
        pending.prepare(store,"good",current,60);String expired=pending.token();rejects(()->pending.confirm(store,expired,current,660),"exact expiry boundary");
        pending.prepare(store,"good",current,700);String time=pending.token();rejects(()->pending.confirm(store,time,current,699),"clock moved backwards");
        pending.prepare(store,"good",current,800);rejects(()->pending.confirm(store,"wrong",current,801),"incorrect token");
        pending.prepare(store,"good",current,900);String legit=pending.token();rejects(()->new BackupRestore().confirm(store,legit,current,901),"token cannot cross menus");
        current.failed=true;pending.prepare(store,"good",current,910);String fault=pending.token();ok(pending.confirm(store,fault,current,911).failed,"confirmed restore preserves fault");
        pending.prepare(store,"good",current,920);String revoked=pending.token();pending.clear();rejects(()->pending.confirm(store,revoked,current,921),"menu closure discards confirmation");
        System.out.println("BackupRestoreTest: "+checks+" checks passed");
    }
}
