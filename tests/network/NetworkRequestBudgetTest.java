import dev.ssscfw.venusmod.infrastructure.network.core.NetworkRequestBudget;
import java.util.UUID;

public final class NetworkRequestBudgetTest {
    private static int assertions;
    private static void check(boolean v,String message){assertions++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        var limiter=new NetworkRequestBudget(4,2);
        var alice=new UUID(0,1);var bob=new UUID(0,2);var eve=new UUID(0,3);
        check(limiter.tryAcquire(100,alice),"first Alice");
        check(limiter.tryAcquire(100,alice),"second Alice");
        check(!limiter.tryAcquire(100,alice),"player limit across reopened menus");
        check(limiter.tryAcquire(100,bob),"first Bob");
        check(limiter.tryAcquire(100,bob),"second Bob");
        check(!limiter.tryAcquire(100,eve),"global limit");
        for(int i=0;i<10000;i++)check(!limiter.tryAcquire(100,new UUID(0,1000+i)),"flood rejected");
        check(limiter.tryAcquire(101,eve),"next tick reopens budget");
        check(limiter.tryAcquire(5,alice),"a new clock epoch resets safely");
        boolean invalid=false;try{new NetworkRequestBudget(0,1);}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"zero global capacity rejected");
        System.out.println("NetworkRequestBudgetTest: "+assertions+" assertions passed");
    }
}
