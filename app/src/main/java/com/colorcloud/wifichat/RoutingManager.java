package com.colorcloud.wifichat;

import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * Created by wangqilin on 4/3/16.
 */
public class RoutingManager {
    private static final String TAG = "RoutingManager";
    private WiFiDirectActivity wiFiDirectActivity = null;

    private RoutingManager() {
    }

    private static final RoutingManager instance = new RoutingManager();

    public static RoutingManager getInstance() {
        return instance;
    }

    // by right singleton should have no argument, but here we need a reference to call the methods in WiFiDirectActivity
    public void init(WiFiDirectActivity wiFiDirectActivity) {
        this.wiFiDirectActivity = wiFiDirectActivity;
    }

    public void connectionTest(final String formattedMsg) {
        connectionTest(formattedMsg, 0);
    }

    public void connectionTest(final String formattedMsg, final int index) {
        Log.d(TAG, "connectionTest called with index = " + index);

        final int max = PersistentGroupPeers.getInstance().getPersistentGroupPeers().size();

        if (index >= max - 1) {
            return;
        }

        String macAddr = PersistentGroupPeers.getInstance().getPersistentGroupPeers().get(index);

        // don't send msg to self
        if (macAddr.equalsIgnoreCase(wiFiDirectActivity.myDevice.deviceAddress)) {
            connectionTest(formattedMsg, index + 1);
        }

        // compose WifiP2pConfig object
        WifiP2pConfig wifiP2pConfig = new WifiP2pConfig();
        wifiP2pConfig.deviceAddress = macAddr.toLowerCase(); // toLowerCase() is IMPORTANT!
        wifiP2pConfig.groupOwnerIntent = -1;
        wifiP2pConfig.wps = new WpsInfo();

        wiFiDirectActivity.getWifiP2pManager().connect(wiFiDirectActivity.getChannel(), wifiP2pConfig, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                WiFiDirectBroadcastReceiver.connected = true;

                // push out the message
                ConnectionService.pushOutMessage(formattedMsg);
                // TODO: ack here

                //disconnect
                wiFiDirectActivity.disconnect();

                // move to the next client device
                connectionTest(formattedMsg, index + 1);
            }

            @Override
            public void onFailure(int reason) {
                WiFiDirectBroadcastReceiver.connected = false;

                Log.e(TAG, "*** connection failed " + reason);

                // move to the next device
                connectionTest(formattedMsg, index + 1);
            }
        });
    }
}
