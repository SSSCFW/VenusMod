package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** サーバーthread上の要求予算。画面を開き直してもプレイヤー単位の上限を回避できない。 */
public final class NetworkRequestBudget {
    private final int globalLimit;
    private final int playerLimit;
    private final Map<UUID,Integer> acceptedByPlayer=new HashMap<>();
    private long tick=Long.MIN_VALUE;
    private int accepted;

    public NetworkRequestBudget(int globalLimit,int playerLimit){
        if(globalLimit<1||playerLimit<1||playerLimit>globalLimit)
            throw new IllegalArgumentException("要求上限は正の数で、個人上限は全体上限以下です");
        this.globalLimit=globalLimit;this.playerLimit=playerLimit;
    }
    public boolean tryAcquire(long now,UUID player){
        Objects.requireNonNull(player);
        if(now!=tick){tick=now;accepted=0;acceptedByPlayer.clear();}
        // 拒否したUUIDはMapに追加しない。保持件数は受理上限で必ず有界になる。
        if(accepted>=globalLimit)return false;
        int count=acceptedByPlayer.getOrDefault(player,0);
        if(count>=playerLimit)return false;
        acceptedByPlayer.put(player,count+1);accepted++;return true;
    }
}
