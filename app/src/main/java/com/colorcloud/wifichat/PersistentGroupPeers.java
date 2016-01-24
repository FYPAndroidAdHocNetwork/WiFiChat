package com.colorcloud.wifichat;

import android.net.wifi.p2p.WifiP2pConfig;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

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

    // Here a Hashset is used to avoid duplicates
    Set<WifiP2pConfig> persistentGroupPeers = new HashSet<WifiP2pConfig>();

    public void add(WifiP2pConfig wifiP2pConfig) {
        persistentGroupPeers.add(wifiP2pConfig);
        Log.d(TAG, "PersistentGroupPeers added, now the set is:");
        PersistentGroupPeers.getInstance().printPersistentGroupPeers();
    }

    public void reset() {
        persistentGroupPeers.clear();
    }

    public boolean isEmpty() {
        return persistentGroupPeers.size() == 0 ? true : false;
    }

    // helper method for debugging
    public void printPersistentGroupPeers() {
        for (WifiP2pConfig wifiP2pConfig : persistentGroupPeers) {
            Log.d(TAG, wifiP2pConfig.toString());
        }
    }
}
