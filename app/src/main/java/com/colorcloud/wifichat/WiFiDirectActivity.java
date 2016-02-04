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
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.colorcloud.wifichat.DeviceListFragment.DeviceActionListener;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements ChannelListener, DeviceActionListener {

    public static final String TAG = "WiFiDirectActivity";
    public static String partnerDevice;
    public static WifiP2pDevice mydevice;
    public static List<WifiP2pDevice> lstPeers = new ArrayList<WifiP2pDevice>();
    private final IntentFilter intentFilter = new IntentFilter();
    private BroadcastReceiver receiver = null;
    private Intent serviceIntent = null;
    private WifiP2pManager wifiP2pManager;
    private Channel channel;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

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
            // P2P on/off option is commented for the time being
//            case R.id.btn_direct_enable:
//                if (wifiP2pManager != null && channel != null) {
//
//                    // Since this is the system wireless settings activity, it's
//                    // not going to send us a result. We will be notified by
//                    // WiFiDeviceBroadcastReceiver instead.
//
//                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
//                } else {
//                    Log.e(TAG, "channel or wifiP2pManager is null");
//                }
//                return true;

            case R.id.btn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
                    return true;
                }

                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();  // show progressbar when discoverying.

                wifiP2pManager.discoverPeers(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
//                        Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();

// below code can be used when determine the reachability of a specific client
//                        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
////                        int rssi = wifi.getConnectionInfo().getRssi();
////                        Toast.makeText(WiFiDirectActivity.this, "rssi: " + rssi, Toast.LENGTH_SHORT).show();
//
//                        List<ScanResult> scanResults = wifiManager.getScanResults();
//
//                        int numOfResults = scanResults.size();
//                        Log.d(TAG, "num of points: " + numOfResults);
//                        for (int i = 0; i < numOfResults; i++) {
//                            String ssid = scanResults.get(i).SSID.toString();
//                            int rssi = scanResults.get(i).level;
//                            Log.d(TAG, "ssid: " + ssid + "; rssi: " + rssi);
//                        }
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;

            case R.id.btn_create_group:
                channel = wifiP2pManager.initialize(this, getMainLooper(), null);
                wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if (group != null) {
                            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                        @Override
                                        public void onSuccess() {
                                            // add current device's MAC into the peer list
                                            PersistentGroupPeers.getInstance().add(getWiFiDirectMacAddress());
                                        }

                                        @Override
                                        public void onFailure(int reason) {
                                            Log.d(TAG, "failed to create persistent group: " + reason);
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(int reason) {
                                    Log.d(TAG, "failed to remove existing group: " + reason);
                                }
                            });
                        } else {
                            wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    // add current device's MAC into the peer list
                                    PersistentGroupPeers.getInstance().add(getWiFiDirectMacAddress());
                                }

                                @Override
                                public void onFailure(int reason) {
                                    Log.d(TAG, "failed to create persistent group: " + reason);
                                }
                            });
                        }
                    }
                });

                return true;

            case R.id.btn_broadcast_connection:
                // TODO: 1/2/16 primary group owner broadcast the connection info here
                PersistentGroupPeers.getInstance().printPersistentGroupPeers();
                return true;

            case R.id.btn_msg:
                // TODO: 1/2/16 uncomment below when the logics are complete
//                if (PersistentGroupPeers.getInstance().isEmpty()) {
//                    Toast.makeText(WiFiDirectActivity.this, "This device does not belong to any group yet", Toast.LENGTH_SHORT).show();
//                } else {
//                    this.startChatActivity(null);
//                }

                this.startChatActivity(null);

                return true;

            case R.id.btn_reset:
                PersistentGroupPeers.getInstance().reset();
                Toast.makeText(WiFiDirectActivity.this, "Peer device list reset", Toast.LENGTH_SHORT).show();
                return true;

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
//                    Log.d(TAG, "Wait Done");
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
//                    Log.d(TAG, "Disconnect failed. Reason : 1=error, 2=busy; " + reasonCode);
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
//        Log.d(TAG, "onP2pConnected : p2p connected, socket server and client selector started.");
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
//                Log.d(TAG, "startChatActivity : p2p connection is missing, do nothng...");
                return;
            }

//            Log.d(TAG, "startChatActivity : start chat activity fragment..." + initMsg);
            Intent i = new Intent(this, ChatActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            i.putExtra("FIRST_MSG", initMsg);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Start chat activity Failed", Toast.LENGTH_SHORT).show();
        }
    }

    // MAC address used in WiFi-direct is different from that in WiFi
    public static String getWiFiDirectMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ntwInterface : interfaces) {

                if (ntwInterface.getName().equalsIgnoreCase("p2p0")) {
                    byte[] byteMac = ntwInterface.getHardwareAddress();
                    if (byteMac == null) {
                        return null;
                    }
                    StringBuilder strBuilder = new StringBuilder();
                    for (int i = 0; i < byteMac.length; i++) {
                        strBuilder.append(String.format("%02X:", byteMac[i]));
                    }

                    if (strBuilder.length() > 0) {
                        strBuilder.deleteCharAt(strBuilder.length() - 1);
                    }

                    return strBuilder.toString();
                }

            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return null;
    }
}
