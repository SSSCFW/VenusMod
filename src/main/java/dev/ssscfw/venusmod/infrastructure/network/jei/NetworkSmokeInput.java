package dev.ssscfw.venusmod.infrastructure.network.jei;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** 開発スモーク専用。ゲーム側のheadless設定を変更せず、隔離DISPLAYの別JVMへ入力を委譲する。 */
final class NetworkSmokeInput implements AutoCloseable {
    private final Process process;
    private final BufferedWriter commands;
    private final BlockingQueue<String> replies=new ArrayBlockingQueue<>(4);
    private boolean closed;

    NetworkSmokeInput(Path helper,Path log)throws Exception{
        Path source=helper.toRealPath();
        if(!source.getFileName().toString().equals("JeiNativeInput.java"))throw new IllegalArgumentException("Unexpected input helper");
        Files.createDirectories(log.toAbsolutePath().getParent());
        process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djava.awt.headless=false",source.toString())
                .redirectError(log.toFile()).start();
        commands=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
        Thread reader=new Thread(()->{
            try(var output=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                for(String line;(line=output.readLine())!=null;)if(!replies.offer(line))break;
            }catch(IOException ignored){}finally{replies.offer("EOF");}
        },"venusmod-native-input-replies");
        reader.setDaemon(true);reader.start();
        try{expect("READY",15);}catch(Exception failure){close();throw failure;}
    }
    long pid(){return process.pid();}
    void click(int x,int y){if(x<0||y<0||x>16384||y>16384)throw new IllegalArgumentException("Pointer out of bounds");send("CLICK "+x+" "+y);}
    void key(int code){if(code<0||code>65535)throw new IllegalArgumentException("Invalid key");send("KEY "+code);}
    void clear(){send("CLEAR");}
    void type(String text){
        if(text==null||text.length()>96||!text.matches("[a-z0-9_ .:/-]*"))throw new IllegalArgumentException("Unsupported smoke input");
        if(!text.isEmpty())send("TYPE "+text);else if(closed)throw new IllegalStateException("Input helper closed");
    }
    private void send(String command){
        if(closed)throw new IllegalStateException("Input helper closed");
        try{commands.write(command);commands.newLine();commands.flush();expect("OK",30);}
        catch(Exception failure){close();throw new IllegalStateException("Native input failed: "+command,failure);}
    }
    private void expect(String expected,int seconds)throws Exception{
        String response=replies.poll(seconds,TimeUnit.SECONDS);
        if(!expected.equals(response))throw new IOException("Input helper expected "+expected+", got "+response);
    }
    @Override public void close(){
        if(closed)return;closed=true;
        // EOFでworkerのfinallyへ進め、押したキーを解放する。通常プレイのプロセスには触れない。
        try{commands.close();}catch(IOException ignored){}
        try{if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroy();if(!process.waitFor(1,TimeUnit.SECONDS))process.destroyForcibly();}}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();process.destroyForcibly();}
    }
}
