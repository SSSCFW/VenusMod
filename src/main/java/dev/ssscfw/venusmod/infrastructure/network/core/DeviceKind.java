package dev.ssscfw.venusmod.infrastructure.network.core;

/** 独立したネットワーク機器。未実装のプロトコルを機種名だけで有効にしない。 */
public enum DeviceKind {
    PC("network_pc",false,false), L2_SWITCH("network_l2_switch",true,false),
    L3_SWITCH("network_l3_switch",true,true), ROUTER("network_router",false,true),
    ONU("network_onu",true,false), OLT("network_olt",true,false),
    SERVER("network_server",false,false), REDSTONE_TERMINAL("network_redstone_terminal",false,false);
    private final String id;
    private final boolean bridge,routing;
    DeviceKind(String id,boolean bridge,boolean routing){this.id=id;this.bridge=bridge;this.routing=routing;}
    public String id(){return id;}
    public boolean bridge(){return bridge;}
    public boolean routing(){return routing;}
    public boolean optical(){return this==ONU||this==OLT;}
}
