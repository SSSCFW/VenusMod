import dev.ssscfw.venusmod.infrastructure.network.core.*;

public final class TransferControlDraftTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void reject(Runnable call){checks++;try{call.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var draft=new TransferControlDraft();
        check(draft.idText().isEmpty()&&draft.priority()==TransferPriority.NORMAL,"initial draft");
        reject(draft::priorityCommand);reject(draft::requestCancellation);reject(draft::confirmCancellation);
        for(String id:new String[]{"0","-1","+1","1.2","1e3","1 2","1;fault","1\n","\t1","１","9223372036854775808","#","##1","9".repeat(21)}){
            draft.idText(id);reject(draft::priorityCommand);reject(draft::requestCancellation);
            check(!draft.cancellationPending(),"invalid id never arms stop");
        }
        for(String id:new String[]{"1","00012","#12"," 12 ","9223372036854775807"}){
            draft.idText(id);draft.priority(TransferPriority.HIGH);
            long expected=id.replace("#","").strip().equals("1")?1:id.length()==19?Long.MAX_VALUE:12;
            check(draft.priorityCommand().equals("transfer priority "+expected+" high"),"canonical priority command");
            draft.requestCancellation();check(draft.cancellationPending(),"armed only");
            check(draft.confirmCancellation().equals("transfer cancel "+expected),"confirmed same target");
            check(!draft.cancellationPending(),"confirmation consumed");reject(draft::confirmCancellation);
        }
        draft.idText("5");draft.requestCancellation();draft.idText("6");reject(draft::confirmCancellation);
        draft.requestCancellation();draft.priority(TransferPriority.LOW);reject(draft::confirmCancellation);
        draft.requestCancellation();draft.dismissCancellation();reject(draft::confirmCancellation);
        check(draft.idText().equals("6")&&draft.priority()==TransferPriority.LOW,"dismiss retains drafts");
        reject(()->draft.idText(null));reject(()->draft.priority(null));
        // 既存CLIを使用。別送信元のIDや完了後のIDで他の転送/設定を変更しない。
        var sim=new NetworkSimulator();
        var a=new NetworkProfile(DeviceKind.PC);a.address="10.170.0.10";
        var b=new NetworkProfile(DeviceKind.SERVER);b.address="10.170.0.20";
        sim.put("a",a);sim.put("b",b);sim.link(new NetworkSimulator.Link("wire","a",1,"b",3,CableGrade.CAT5E,2,false));
        sim.startTransfer("a",b.address,1_000_000_000L,TransferPriority.NORMAL);
        sim.startTransfer("b",a.address,1_000_000_000L,TransferPriority.NORMAL);
        var own=sim.flows().stream().filter(f->f.source.equals("a")).findFirst().orElseThrow();
        var other=sim.flows().stream().filter(f->f.source.equals("b")).findFirst().orElseThrow();
        String before=a.encode();draft.idText(""+other.id);draft.priority(TransferPriority.HIGH);
        NetworkCli.run(sim,"a",draft.priorityCommand());check(other.priority()==TransferPriority.NORMAL,"other source priority protected");
        draft.requestCancellation();NetworkCli.run(sim,"a",draft.confirmCancellation());check(sim.flows().size()==2,"other source cancellation rejected");
        draft.idText(""+own.id);NetworkCli.run(sim,"a",draft.priorityCommand());check(own.priority()==TransferPriority.HIGH,"own priority changed");
        draft.requestCancellation();check(sim.flows().size()==2,"arming is not sending");
        NetworkCli.run(sim,"a",draft.confirmCancellation());check(sim.flows().size()==1&&sim.flows().getFirst()==other,"cancel only own flow");
        draft.requestCancellation();NetworkCli.run(sim,"a",draft.confirmCancellation());check(sim.flows().size()==1,"stale id not reused");
        check(a.encode().equals(before),"management does not change settings");
        System.out.println("TransferControlDraftTest: "+checks+" checks passed");
    }
}
