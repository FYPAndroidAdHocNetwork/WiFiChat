package com.colorcloud.wifichat;

import android.net.wifi.p2p.WifiP2pConfig;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wangqilin on 21/1/16.
 */
public class PersistentGroupPeers {

    private static final String TAG = "PersistentGroupPeers";

    private PersistentGroupPeers() {

    }

    // singleton implementation
    private static final PersistentGroupPeers instance = new PersistentGroupPeers();

    public static PersistentGroupPeers getInstance() {
        return instance;
    }

    List<WifiP2pConfig> persistentGroupPeers = new ArrayList<WifiP2pConfig>();

    public void add(WifiP2pConfig wifiP2pConfig) {
        persistentGroupPeers.add(wifiP2pConfig);
        Log.d(TAG, "PersistentGroupPeers added");
        PersistentGroupPeers.getInstance().printPersistentGroupPeers();
    }

    public void reset() {
        Log.d(TAG, "PersistentGroupPeers before reseting:");
        PersistentGroupPeers.getInstance().printPersistentGroupPeers();
        persistentGroupPeers.clear();
    }

    public void printPersistentGroupPeers() {
        for (int i = 0; i < persistentGroupPeers.size(); i++) {
            Log.d(TAG, persistentGroupPeers.get(i).toString());
        }
    }
}
