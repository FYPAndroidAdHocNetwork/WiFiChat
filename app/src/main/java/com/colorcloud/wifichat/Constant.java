package com.colorcloud.wifichat;

public final class Constant {

    public static final int MSG_NULL = 0;
    public static final int MSG_STARTSERVER = 1001;
    public static final int MSG_STARTCLIENT = 1002;
    public static final int MSG_CONNECT = 1003;
    public static final int MSG_DISCONNECT = 1004;   // p2p disconnect
    public static final int MSG_PUSHOUT_DATA = 1005;
    public static final int MSG_NEW_CLIENT = 1006;
    public static final int MSG_FINISH_CONNECT = 1007;
    public static final int MSG_PULLIN_DATA = 1008;
    public static final int MSG_REGISTER_ACTIVITY = 1009;

    public static final int MSG_SELECT_ERROR = 2001;
    public static final int MSG_BROKEN_CONN = 2002;  // network disconnect

    // message format
    public static final int DEVICE_MAC_ADDRESS = 1;
    public static final int GROUP_MAC_ADDRESS = 2;
    public static final int MESSAGE = 3;
    public static final int IMMEDIATE_ACKNOWLEDGEMENT = 4;
    public static final int ROUTING_ACKNOWLEDGEMENT = 5;

}
