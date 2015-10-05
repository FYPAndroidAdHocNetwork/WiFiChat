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

import com.colorcloud.wifichat.R;
import com.colorcloud.wifichat.DeviceListFragment.DeviceActionListener;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = "PTP_Recv";
	public static boolean connected = false;
	
    private WifiP2pManager manager;
    private WifiP2pDevice device;
    private Channel channel;
    private WiFiDirectActivity activity;
    ProgressDialog progressDialog = null;

    public WiFiDirectBroadcastReceiver() { super(); }
    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param activity activity associated with the receiver
     */
    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, WiFiDirectActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
    	try{
	        String action = intent.getAction();
	        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {  // this devices's wifi direct enabled state.
	
	            // UI update to indicate wifi p2p status.
	            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
	            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
	                // Wifi Direct mode is enabled
	                activity.setIsWifiP2pEnabled(true);
	            } else {
	                activity.setIsWifiP2pEnabled(false);
	                activity.resetData();
	            }
	            Log.d(TAG, " WIFI_P2P_STATE_CHANGED_ACTION = " + state);
	        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {  
	        	// a list of peers are available after discovery, use PeerListListener to collect
	
	            // request available peers from the wifi p2p manager. This is an
	            // asynchronous call and the calling activity is notified with a
	            // callback on PeerListListener.onPeersAvailable()
	            if (manager != null) {
	                manager.requestPeers(channel, (PeerListListener) activity.getFragmentManager()
	                        .findFragmentById(R.id.frag_list));
	            }
	            Log.d(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION: requestPeers");
	        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
	            if (manager == null) {
	                return;
	            }
	
	            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
	
	            if (networkInfo.isConnected()) {
	                // Connected with the other device, request connection info for group owner IP. Callback inside details fragment.
	                DeviceDetailFragment fragment = (DeviceDetailFragment) activity.getFragmentManager().findFragmentById(R.id.frag_detail);
	                manager.requestConnectionInfo(channel, fragment);  
	            } else {
	                // It's a disconnect
	        		Toast.makeText(context, "Status: Disconnected",  Toast.LENGTH_SHORT).show(); 
	        		
	                activity.resetData();
	                if (manager != null) {
		                manager.requestPeers(channel, (PeerListListener) activity.getFragmentManager()
		                        .findFragmentById(R.id.frag_list));

		            	Log.d(TAG,"Partner ID 2: " + WiFiDirectActivity.partnerDevice);
		                if(WiFiDirectActivity.partnerDevice != null){
		                	Log.d(TAG,"Check WiFi");
		                	WifiManager wifi = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
		                	wifi.setWifiEnabled(true);
			                //int rcap= 10;
			                //int r = 0;
			                //while(!networkInfo.isConnected()){
			                	try{
				                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
	
				                        @Override
				                        public void onSuccess() {
				                            //Toast.makeText(activity, "Discovery Initiated", Toast.LENGTH_SHORT).show();

						                	Log.d(TAG,"Peer Discovery Success");
				                        }
	
				                        @Override
				                        public void onFailure(int reasonCode) {
				                            //Toast.makeText(activity, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
						                	Log.d(TAG,"Peer Discovery Failed");
				                        }
				                    });
				                    manager.requestPeers(channel, (PeerListListener) activity.getFragmentManager()
					                        .findFragmentById(R.id.frag_list));
				                    WifiP2pConfig config = new WifiP2pConfig();
				                    config.deviceAddress = WiFiDirectActivity.partnerDevice;
				                    config.wps.setup = WpsInfo.PBC;
				                	Log.d(TAG,"Before Connecting to partner");
				                    activity.connect(config);
				                	Log.d(TAG,"After Connecting to partner");
				                    if(networkInfo.isConnectedOrConnecting()){
						    	        Handler handler = new Handler(); 
						    	        handler.postDelayed(new Runnable() {public void run() {Log.d(TAG,"Wait Done 3");}}, 2000);
				                    }
			                	}
			                	catch(Exception e) {
			                		Log.d(TAG,"2 error: " + e.toString());
			                		//r++;
			                		//if(r >= rcap) break;
					    	        Handler handler = new Handler(); 
					    	        handler.postDelayed(new Runnable() {public void run() {Log.d(TAG,"Wait Done 4");}}, 500);
			                	}
			                }
		                //}
		            }
	            }
	            Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION = " + networkInfo.describeContents());
	        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {  
	        	// this device details has changed(name, connected, etc)
	            DeviceListFragment fragment = (DeviceListFragment) activity.getFragmentManager().findFragmentById(R.id.frag_list);
	            fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
	            Log.d(TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION ");
	        }
    	}
    	catch(Exception e) { 
    		Log.d(TAG,"Error: "+e.toString());
    		Toast.makeText(context, "Disconnected",  Toast.LENGTH_SHORT).show(); 
            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        	Log.d(TAG,"Partner ID 1: " + WiFiDirectActivity.partnerDevice);
            if(WiFiDirectActivity.partnerDevice != null){
                try{
                	Log.d(TAG,"Check WiFi");
                	WifiManager wifi = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
                	wifi.setWifiEnabled(true);
                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                            //Toast.makeText(activity, "Discovery Initiated", Toast.LENGTH_SHORT).show();

		                	Log.d(TAG,"Peer Discovery Success");
                        }

                        @Override
                        public void onFailure(int reasonCode) {
                            //Toast.makeText(activity, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
		                	Log.d(TAG,"Peer Discovery Failed");
                        }
                    });
                    manager.requestPeers(channel, (PeerListListener) activity.getFragmentManager()
	                        .findFragmentById(R.id.frag_list));
                    
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = WiFiDirectActivity.partnerDevice;
                    config.wps.setup = WpsInfo.PBC;
                	Log.d(TAG,"Before Connecting to partner");
                    activity.connect(config);
                	Log.d(TAG,"After Connecting to partner");
                    if(networkInfo.isConnectedOrConnecting()){
		    	        Handler handler = new Handler(); 
		    	        handler.postDelayed(new Runnable() {public void run() {Log.d(TAG,"Wait Done 3");}}, 2000);
                    }
            	}
            	catch(Exception ex) {
            		Log.d(TAG,"2 error: " + ex.toString());
	    	        Handler handler = new Handler(); 
	    	        handler.postDelayed(new Runnable() {public void run() {Log.d(TAG,"Wait Done 4");}}, 500);
            	}
	    	}
    	}
    }
}
