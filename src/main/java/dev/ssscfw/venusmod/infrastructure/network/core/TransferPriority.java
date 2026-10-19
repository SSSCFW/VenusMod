package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.Locale;

/** 実行中の仮想転送の相対重み。厳密優先やDSCPマーキングではない。 */
public enum TransferPriority {
    LOW(1),NORMAL(2),HIGH(4);
    private final int weight;
    TransferPriority(int weight){this.weight=weight;}
    public int weight(){return weight;}
    public String label(){return name().toLowerCase(Locale.ROOT);}
    public static TransferPriority parse(String text){
        if(text==null)throw new IllegalArgumentException("優先度はlow/normal/highです");
        try{return valueOf(text.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("優先度はlow/normal/highです");}
    }
}
