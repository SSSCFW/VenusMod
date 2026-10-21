import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Xvfbの専用クライアント検証からのみ起動する、標準入力パイプのネイティブ入力worker。 */
public final class JeiNativeInput {
    private static final Set<Integer> KEYS=Set.of(KeyEvent.VK_ENTER,KeyEvent.VK_ESCAPE,
            KeyEvent.VK_UP,KeyEvent.VK_DOWN,KeyEvent.VK_PAGE_UP,KeyEvent.VK_PAGE_DOWN,
            KeyEvent.VK_BACK_SPACE,KeyEvent.VK_TAB);
    public static void main(String[] args)throws Exception{
        Robot robot=new Robot();robot.setAutoDelay(40);
        try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){
            reply("READY");
            for(String line;(line=input.readLine())!=null;){
                if(line.length()>128)throw new IllegalArgumentException("Input command too long");
                if(line.startsWith("CLICK ")){
                    String[] parts=line.split(" ",-1);
                    if(parts.length!=3)throw new IllegalArgumentException("Invalid click");
                    int x=Integer.parseInt(parts[1]),y=Integer.parseInt(parts[2]);
                    if(x<0||y<0||x>16384||y>16384)throw new IllegalArgumentException("Pointer out of bounds");
                    robot.mouseMove(x,y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                }else if(line.startsWith("KEY ")){
                    int code=Integer.parseInt(line.substring(4));
                    if(!KEYS.contains(code))throw new IllegalArgumentException("Unsupported key");key(robot,code);
                }else if(line.equals("CLEAR")){
                    robot.keyPress(KeyEvent.VK_CONTROL);key(robot,KeyEvent.VK_A);robot.keyRelease(KeyEvent.VK_CONTROL);key(robot,KeyEvent.VK_BACK_SPACE);
                }else if(line.startsWith("TYPE ")){
                    String text=line.substring(5);
                    if(text.length()>96||!text.matches("[a-z0-9_ .:/-]*"))throw new IllegalArgumentException("Unsupported text");
                    for(char c:text.toCharArray()){
                        boolean shift=c=='_'||c==':';if(shift)robot.keyPress(KeyEvent.VK_SHIFT);
                        key(robot,c=='_'?KeyEvent.VK_MINUS:c==':'?KeyEvent.VK_SEMICOLON:KeyEvent.getExtendedKeyCodeForChar(c));
                        if(shift)robot.keyRelease(KeyEvent.VK_SHIFT);
                    }
                }else throw new IllegalArgumentException("Unknown input command");
                reply("OK");
            }
        }finally{
            robot.keyRelease(KeyEvent.VK_CONTROL);robot.keyRelease(KeyEvent.VK_SHIFT);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }
    private static void key(Robot robot,int code){robot.keyPress(code);robot.keyRelease(code);}
    private static void reply(String value){System.out.println(value);System.out.flush();}
}
