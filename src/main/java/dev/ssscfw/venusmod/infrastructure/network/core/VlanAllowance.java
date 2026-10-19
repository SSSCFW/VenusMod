package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 不変のトランク許可集合。文字列は設定時だけ解析し、通信時はビット参照する。 */
public final class VlanAllowance {
    public static final int MAX_TEXT=256, MIN_VLAN=1, MAX_VLAN=4094;
    public static final VlanAllowance ALL=full();
    public static final VlanAllowance NONE=new VlanAllowance(new BitSet(),"none");
    private final BitSet vlans;
    private final String text;
    private VlanAllowance(BitSet bits,String text){this.vlans=(BitSet)bits.clone();this.text=text;}
    private static VlanAllowance full(){var bits=new BitSet(MAX_VLAN+1);bits.set(MIN_VLAN,MAX_VLAN+1);return new VlanAllowance(bits,"all");}
    public static VlanAllowance parse(String input){
        if(input==null||input.length()>MAX_TEXT||input.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("許可VLANは制御文字なし256文字以内です");
        String value=input.strip();
        if(value.equalsIgnoreCase("all"))return ALL;
        if(value.equalsIgnoreCase("none"))return NONE;
        if(value.isEmpty())throw new IllegalArgumentException("許可VLANはall、none、または10,20-30の形式です");
        var bits=new BitSet(MAX_VLAN+1);
        for(String part:value.split(",",-1)){
            if(!part.matches("[0-9]{1,4}(-[0-9]{1,4})?"))throw new IllegalArgumentException("許可VLANの形式が不正です: "+part);
            String[] range=part.split("-",-1);int first=Integer.parseInt(range[0]),last=range.length==1?first:Integer.parseInt(range[1]);
            if(first<MIN_VLAN||last>MAX_VLAN||first>last)throw new IllegalArgumentException("VLAN範囲は1〜4094の昇順です");
            bits.set(first,last+1);
        }
        if(bits.equals(ALL.vlans))return ALL;
        var ranges=new ArrayList<String>();
        for(int first=bits.nextSetBit(MIN_VLAN);first>=0;){
            int end=bits.nextClearBit(first)-1;ranges.add(first==end?Integer.toString(first):first+"-"+end);first=bits.nextSetBit(end+1);
        }
        String canonical=String.join(",",ranges);
        if(canonical.length()>MAX_TEXT)throw new IllegalArgumentException("正規化後の許可VLANが256文字を超えます");
        return new VlanAllowance(bits,canonical);
    }
    public boolean contains(int vlan){return vlan>=MIN_VLAN&&vlan<=MAX_VLAN&&vlans.get(vlan);}
    @Override public String toString(){return text;}
    @Override public boolean equals(Object other){return other instanceof VlanAllowance allowed&&vlans.equals(allowed.vlans);}
    @Override public int hashCode(){return vlans.hashCode();}
}
