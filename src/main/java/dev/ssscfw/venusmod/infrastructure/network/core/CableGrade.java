package dev.ssscfw.venusmod.infrastructure.network.core;

/** 距離・帯域は現実の規格値ではなくゲーム用の値。 */
public enum CableGrade {
    CAT5E("cat5e_cable",1_000_000_000L,64,false),
    CAT6("cat6_cable",10_000_000_000L,32,false),
    CAT6A("cat6a_cable",10_000_000_000L,128,false),
    CAT8("cat8_cable",40_000_000_000L,24,false),
    OM3("om3_fiber_cable",10_000_000_000L,256,true),
    OM4("om4_fiber_cable",40_000_000_000L,512,true),
    OS2("os2_fiber_cable",100_000_000_000L,4096,true),
    VENUS("venus_fiber_cable",400_000_000_000L,8192,true);
    private final String id;
    private final long bps;
    private final int maxLength;
    private final boolean optical;
    CableGrade(String id,long bps,int maxLength,boolean optical){this.id=id;this.bps=bps;this.maxLength=maxLength;this.optical=optical;}
    public String id(){return id;}
    public long bps(){return bps;}
    public int maxLength(){return maxLength;}
    public boolean optical(){return optical;}
    public static String speed(long bps) {
        return bps >= 1_000_000_000L ? String.format(java.util.Locale.ROOT,"%.2f Gb/s",bps/1e9)
                : String.format(java.util.Locale.ROOT,"%.2f Mb/s",bps/1e6);
    }
}
