package com.colorcloud.wifichat;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.colorcloud.wifichat.Constant.PERSISTENT_GROUP_PEERS_TOKEN;

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

    // Here a ArrayList is used to preserve the orders
    List<String> persistentGroupPeers = new ArrayList<String>();

    public void add(String peerDeviceMACAddress) {
        for (String existingMACAddress : persistentGroupPeers) {
            if (existingMACAddress.equals(peerDeviceMACAddress)) {
                Log.d(TAG, "a duplicate mac address");
                return;
            }
        }

        persistentGroupPeers.add(peerDeviceMACAddress);
        Log.d(TAG, "PersistentGroupPeers added, now the set is:");
        PersistentGroupPeers.getInstance().printPersistentGroupPeers();
    }

    public void reset() {
        persistentGroupPeers.clear();
    }

    public boolean isEmpty() {
        return persistentGroupPeers.size() == 0 ? true : false;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    // helper method for debugging
    public void printPersistentGroupPeers() {
        Log.d(TAG, "number of items: " + persistentGroupPeers.size());
        for (String peerDeviceMACAddress : persistentGroupPeers) {
            Log.d(TAG, peerDeviceMACAddress);
        }
    }
}
