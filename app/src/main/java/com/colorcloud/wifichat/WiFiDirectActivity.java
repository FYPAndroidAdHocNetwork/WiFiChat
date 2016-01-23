/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.colorcloud.wifichat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.colorcloud.wifichat.DeviceListFragment.DeviceActionListener;

import java.util.ArrayList;
import java.util.List;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements ChannelListener, DeviceActionListener {

    public static final String TAG = "PTP_Activity";
    public static String partnerDevice;
    public static WifiP2pDevice mydevice;
    public static List<WifiP2pDevice> lstPeers = new ArrayList<WifiP2pDevice>();

    private final IntentFilter intentFilter = new IntentFilter();
    private BroadcastReceiver receiver = null;
    private Intent serviceIntent = null;

    ConnectionManager mConnMan = null;

    private WifiP2pManager wifiP2pManager;
    private Channel channel;

    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    private boolean peerDiscovered = false;

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.main);   // statically draw two <fragment class=>

            // add necessary intent values to be matched.

            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);

            ((WiFiChatApp) getApplication()).mP2pChannel = channel;
            ((WiFiChatApp) getApplication()).mHomeActivity = this;

            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

            Log.d(TAG, "onCreate : home activity created wifip2p wifiP2pManager and channel: " + wifiP2pManager.toString() + " :: " + channel);
        } catch (Exception e) {
            Toast.makeText(this, "On Create Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * register the BroadcastReceiver with the intent values to be matched
     */
    @Override
    public void onResume() {
        try {
            super.onResume();

            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);
            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

            ((WiFiChatApp) getApplication()).mP2pChannel = channel;
            ((WiFiChatApp) getApplication()).mHomeActivity = this;
            receiver = new WiFiDirectBroadcastReceiver(wifiP2pManager, channel, this);
            registerReceiver(receiver, intentFilter);

        } catch (Exception e) {
            Toast.makeText(this, "On Resume Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPause() {
        try {
            super.onPause();
            unregisterReceiver(receiver);
            stopService(serviceIntent);
        } catch (Exception e) {
            Toast.makeText(this, "On Pause Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.btn_direct_enable:
                if (wifiP2pManager != null && channel != null) {

                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.

                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } else {
                    Log.e(TAG, "channel or wifiP2pManager is null");
                }
                return true;

            case R.id.btn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
                    return true;
                }

                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();  // show progressbar when discoverying.

                wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
                        peerDiscovered = true;

// below code can be used when determine the reachability of a specific client
//                        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
////                        int rssi = wifi.getConnectionInfo().getRssi();
////                        Toast.makeText(WiFiDirectActivity.this, "rssi: " + rssi, Toast.LENGTH_SHORT).show();
//
//                        List<ScanResult> scanResults = wifiManager.getScanResults();
//
//                        int numOfResults = scanResults.size();
//                        Log.d("######", "num of points: " + numOfResults);
//                        for (int i = 0; i < numOfResults; i++) {
//                            String ssid = scanResults.get(i).SSID.toString();
//                            int rssi = scanResults.get(i).level;
//                            Log.d("######", "ssid: " + ssid + "; rssi: " + rssi);
//                        }
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;

            case R.id.btn_reset_persistent_group:
                PersistentGroupPeers.getInstance().reset();

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);

    }

    @Override
    public void connect(final WifiP2pConfig config) {
        try {
            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);
            stopService(serviceIntent);
            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

            // perform p2p connect upon users click the connect button. after connection, wifiP2pManager request connection info.
            wifiP2pManager.connect(channel, config, new ActionListener() {

                @Override
                public void onSuccess() {
                    WiFiDirectBroadcastReceiver.connected = true;
                    PersistentGroupPeers.getInstance().add(config);
                }

                @Override
                public void onFailure(int reason) {
                    Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
                    WiFiDirectBroadcastReceiver.connected = false;
                }
            });
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    Log.d(TAG, "Wait Done");
                }
            }, 1000);
        } catch (Exception e) {
            Toast.makeText(this, "Connect Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * disconnect from group owner.
     */
    @Override
    public void disconnect() {
        try {
            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);
            stopService(serviceIntent);
            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

            final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
            fragment.resetViews();
            wifiP2pManager.removeGroup(channel, new ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason : 1=error, 2=busy; " + reasonCode);
                }

                @Override
                public void onSuccess() {
                    fragment.getView().setVisibility(View.GONE);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Disconnect Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The channel to the framework(WiFi direct) has been disconnected.
     * This is diff than the p2p connection to group owner.
     */
    @Override
    public void onChannelDisconnected() {
        try {

            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);
            stopService(serviceIntent);
            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

            // we will try once more,
            if (wifiP2pManager != null && !retryChannel) {
                Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
                resetData();
                retryChannel = true;
                wifiP2pManager.initialize(this, getMainLooper(), this);
            } else {
                Toast.makeText(this, "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Channel disconnected Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void cancelDisconnect() {
        try {

            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);
            stopService(serviceIntent);
            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

	        /*
             * A cancel abort request by user. Disconnect i.e. removeGroup if
	         * already connected. Else, request WifiP2pManager to abort the ongoing
	         * request
	         */
            if (wifiP2pManager != null) {
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                    disconnect();
                } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE || fragment.getDevice().status == WifiP2pDevice.INVITED) {
                    wifiP2pManager.cancelConnect(channel, new ActionListener() {

                        @Override
                        public void onSuccess() {
                            Toast.makeText(WiFiDirectActivity.this, "Aborting connection", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(int reasonCode) {
                            Toast.makeText(WiFiDirectActivity.this, "Connect abort request failed. Reason Code: " + reasonCode, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cancel disconnect Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * socket connected, update connection state.
     */
    public void onP2pConnected() {

        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(this, getMainLooper(), null);
        stopService(serviceIntent);
        serviceIntent = new Intent(this, ConnectionService.class);
        startService(serviceIntent);  // start the connection service

        ((WiFiChatApp) getApplication()).mP2pConnected = true;
        Log.d(TAG, "onP2pConnected : p2p connected, socket server and client selector started.");
        Toast.makeText(WiFiDirectActivity.this, "Connected", Toast.LENGTH_SHORT).show();
    }

    /**
     * launch chat activity
     */
    public void startChatActivity(String initMsg) {
        try {
            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(this, getMainLooper(), null);
            stopService(serviceIntent);
            serviceIntent = new Intent(this, ConnectionService.class);
            startService(serviceIntent);  // start the connection service

            // only starts chat activity when p2p connected
            if (!((WiFiChatApp) getApplication()).mP2pConnected) {
                Log.d(TAG, "startChatActivity : p2p connection is missing, do nothng...");
                return;
            }

            Log.d(TAG, "startChatActivity : start chat activity fragment..." + initMsg);
            Intent i = new Intent(this, ChatActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            i.putExtra("FIRST_MSG", initMsg);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Start chat activity Failed", Toast.LENGTH_SHORT).show();
        }
    }
}
