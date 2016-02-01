package com.colorcloud.wifichat;

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
    Set<String> persistentGroupPeers = new HashSet<String>();

    public void add(String peerDeviceMACAddress) {
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

    // helper method for debugging
    public void printPersistentGroupPeers() {
        for (String peerDeviceMACAddress : persistentGroupPeers) {
            Log.d(TAG, peerDeviceMACAddress);
        }
    }
}
