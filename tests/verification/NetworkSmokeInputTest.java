package dev.ssscfw.venusmod.infrastructure.network.jei;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.Path;

/** Xvfb専用。親がheadlessのままでも、別JVMはネイティブ入力を生成できる。 */
public final class NetworkSmokeInputTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        System.setProperty("java.awt.headless","true");
        check(GraphicsEnvironment.isHeadless(),"Parent must reproduce Minecraft's headless AWT setting");
        boolean rejected=false;try{new Robot();}catch(AWTException expected){rejected=true;}
        check(rejected,"In-process Robot must reproduce the original failure");
        var input=new NetworkSmokeInput(Path.of(args[0]),Path.of(args[1]));
        long pid=input.pid();
        try{
            check(ProcessHandle.of(pid).orElseThrow().isAlive(),"Child started");
            input.click(100,100);input.type("vlan");input.clear();input.type("zz_no_matching_help");input.key(KeyEvent.VK_ESCAPE);
            check(GraphicsEnvironment.isHeadless(),"Parent AWT setting remains unchanged");
            rejected=false;try{input.type("a\nb");}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Reject embedded protocol separator before sending input");
            rejected=false;try{input.type("a".repeat(97));}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Bound native input length");
            rejected=false;try{input.click(-1,1);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Reject invalid pointer coordinate");
            input.type("vlan");check(true,"Invalid input did not break the channel");
        }finally{input.close();}
        check(ProcessHandle.of(pid).map(p->!p.isAlive()).orElse(true),"Child exits on close");
        input.close();check(true,"Close is idempotent");
        rejected=false;try{input.type("vlan");}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"Closed input channel rejects further commands");
        System.out.println("NetworkSmokeInputTest: "+checks+" checks passed");
    }
}
