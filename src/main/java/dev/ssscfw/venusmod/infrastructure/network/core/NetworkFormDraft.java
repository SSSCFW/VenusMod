package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.IOException;
import java.util.*;

/** クライアントの入力途中状態。画面サイズ変更や周期同期から独立して保持する。 */
public final class NetworkFormDraft {
    public record Request(int id,String expected,String edit){}
    private final NetworkForm.View[] latest=new NetworkForm.View[NetworkForm.PAGE_COUNT],baseline=new NetworkForm.View[NetworkForm.PAGE_COUNT];
    private final NetworkForm.Values[] values=new NetworkForm.Values[NetworkForm.PAGE_COUNT];
    private int nextId=1,pendingId,pendingScope,pendingTicks;
    private String message="サーバーから設定を取得しています";
    public NetworkForm.View view(int scope){return baseline[index(scope)];}
    public NetworkForm.Values values(int scope){return values[index(scope)];}
    public String message(){return message;}
    public boolean pending(){return pendingId!=0;}
    public boolean dirty(int scope){int i=index(scope);return baseline[i]!=null&&!baseline[i].values().equals(values[i]);}
    public boolean stale(int scope){int i=index(scope);return baseline[i]!=null&&latest[i]!=null&&!baseline[i].equals(latest[i]);}
    public void edit(NetworkForm.Values edit){if(!pending()){int i=index(edit.scope());if(baseline[i]!=null)values[i]=Objects.requireNonNull(edit);}}
    public void reload(int scope){if(pending())return;int i=index(scope);if(latest[i]!=null){baseline[i]=latest[i];values[i]=latest[i].values();message="現在値を再読込しました (このページの未保存入力は破棄)";}}
    public Request submit(int scope){
        int i=index(scope);if(pending()||baseline[i]==null)return null;
        if(nextId==Integer.MAX_VALUE)nextId=1;int id=nextId++;
        var request=new Request(id,NetworkForm.encode(baseline[i]),NetworkForm.encode(values[i]));
        pendingId=id;pendingScope=scope;pendingTicks=0;message="サーバーへ適用中…";return request;
    }
    public void receive(List<String> pages,int requestId,boolean accepted,String response){
        if(pages.size()!=NetworkForm.PAGE_COUNT)throw new IllegalArgumentException("フォームのページ数が不正です");
        var decoded=new NetworkForm.View[NetworkForm.PAGE_COUNT];
        try{for(int i=0;i<decoded.length;i++){decoded[i]=NetworkForm.decodeView(pages.get(i));if(decoded[i].values().scope()!=i-1||decoded[i].kind()!=decoded[0].kind())throw new IllegalArgumentException("フォームのページ順が不正です");}}
        catch(IOException e){throw new IllegalArgumentException("フォームの同期データが不正です",e);}
        // 全ページを検証してから反映する。古いACKや無関係な周期通知で保存済みとは判定しない。
        boolean acknowledged=pending()&&requestId==pendingId;
        for(int i=0;i<decoded.length;i++){
            latest[i]=decoded[i];
            if(baseline[i]==null||(!(pending()&&i==pendingScope+1)&&!dirty(i-1))){baseline[i]=decoded[i];values[i]=decoded[i].values();}
        }
        if(acknowledged){
            if(accepted){int i=index(pendingScope);baseline[i]=latest[i];values[i]=latest[i].values();}
            pendingId=0;pendingTicks=0;message=response;
        }else if(message.equals("サーバーから設定を取得しています"))message="編集後「適用」で保存。空のIPv4欄は未設定に戻します。";
    }
    public void tick(){if(pending()&&++pendingTicks>=100){pendingId=0;message="応答未確認です。現在値を再読込してから再試行してください。";}}
    private static int index(int scope){int i=scope+1;if(i<0||i>=NetworkForm.PAGE_COUNT)throw new IllegalArgumentException("フォーム範囲外です");return i;}
}
