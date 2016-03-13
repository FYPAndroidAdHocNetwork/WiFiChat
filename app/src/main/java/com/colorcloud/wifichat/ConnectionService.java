package com.colorcloud.wifichat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import static com.colorcloud.wifichat.Constant.IMMEDIATE_ACKNOWLEDGEMENT;
import static com.colorcloud.wifichat.Constant.MSG_BROKEN_CONN;
import static com.colorcloud.wifichat.Constant.MSG_FINISH_CONNECT;
import static com.colorcloud.wifichat.Constant.MSG_NEW_CLIENT;
import static com.colorcloud.wifichat.Constant.MSG_NULL;
import static com.colorcloud.wifichat.Constant.MSG_PULLIN_DATA;
import static com.colorcloud.wifichat.Constant.MSG_PUSHOUT_DATA;
import static com.colorcloud.wifichat.Constant.MSG_REGISTER_ACTIVITY;
import static com.colorcloud.wifichat.Constant.MSG_SELECT_ERROR;
import static com.colorcloud.wifichat.Constant.MSG_STARTCLIENT;
import static com.colorcloud.wifichat.Constant.MSG_STARTSERVER;

public class ConnectionService extends Service {

    private static final String TAG = "ConnectionService";
    private static ConnectionService instance = null;

    private WorkHandler workHandler;
    private MessageHandler messageHandler;
    private ChatActivity chatActivity;    // shall I use weak reference here ?
    private ConnectionManager connectionManager;

    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        initialize();
    }

    private void initialize() {
        if (instance != null) {
            return;
        }

        instance = this;

        workHandler = new WorkHandler("ConnectionService");
        messageHandler = new MessageHandler(workHandler.getLooper());
        connectionManager = new ConnectionManager(this);
        WiFiDirectBroadcastReceiver.config(connectionManager);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initialize();
        return START_STICKY;
    }

    public static ConnectionService getInstance() {
        return instance;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    public Handler getHandler() {
        return messageHandler;
    }

    /**
     * message handler looper to handle all the msg sent to location manager.
     */
    final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }

    /**
     * the main message process loop.
     *
     * @param msg
     */
    private void processMessage(Message msg) {
        switch (msg.what) {
            case MSG_NULL:
                break;
            case MSG_REGISTER_ACTIVITY:
                onActivityRegister((ChatActivity) msg.obj, msg.arg1);
                break;
            case MSG_STARTSERVER:
                connectionManager.startServerSelector();
                break;
            case MSG_STARTCLIENT:
                connectionManager.startClientSelector((String) msg.obj);
                break;
            case MSG_NEW_CLIENT:
                connectionManager.onNewClient((SocketChannel) msg.obj);
                break;
            case MSG_FINISH_CONNECT:
                connectionManager.onFinishConnect((SocketChannel) msg.obj);
                break;
            case MSG_PULLIN_DATA:
                onPullInData((SocketChannel) msg.obj, msg.getData());
                break;
            case MSG_PUSHOUT_DATA:
                onPushOutData((String) msg.obj);
                break;
            case MSG_SELECT_ERROR:
                connectionManager.onSelectorError();
                break;
            case MSG_BROKEN_CONN:
                connectionManager.onBrokenConn((SocketChannel) msg.obj);
                break;
            default:
                break;
        }
    }

    /**
     * register the activity that uses this service.
     */
    private void onActivityRegister(ChatActivity activity, int register) {
        // 1: register; else: de-register
        if (register == 1) {
            chatActivity = activity;
        } else {
            chatActivity = null;    // set to null explicitly to avoid mem leak.
        }
    }

    /**
     * service handle data in come from socket channel
     */
    private void onPullInData(SocketChannel socketChannel, Bundle bundle) {
        String data = bundle.getString("DATA");

        MessageWrapper messageWrapper = MessageWrapper.parseMessageWrapper(data);
        int category = messageWrapper.getCategory();
        String messageBody = messageWrapper.getMessageBody();

        switch (category) {
            case Constant.DEVICE_MAC_ADDRESS:
                // in this case, the messageBody is the device's MAC address
                Log.d(TAG, "client device's MAC address is: " + messageBody);
                PersistentGroupPeers.getInstance().add(messageBody);
                break;

            case Constant.GROUP_MAC_ADDRESS:
                // since GROUP_MAC_ADDRESS is sent by group owner, the whole group enters the multihopState
                WiFiDirectActivity.multihopState = true;

                Log.d(TAG, "GROUP_MAC_ADDRESS: " + messageBody);
                ArrayList<String> persistentGroupPeers = PersistentGroupPeers.parsePersistentGroupPeersString(messageBody);
                PersistentGroupPeers.getInstance().reset();
                PersistentGroupPeers.getInstance().persistentGroupPeers = persistentGroupPeers;
                break;

            case Constant.MESSAGE:
                // reply ack immediately
                long ack = messageWrapper.getAck();
                Log.d(TAG, "ack is " + ack);
                MessageWrapper repliedMessage = new MessageWrapper(IMMEDIATE_ACKNOWLEDGEMENT, "" + ack);
                ConnectionService.pushOutMessage(repliedMessage.toString());

                // pub to all client if this device is server.
                connectionManager.pubDataToAllClients(data, socketChannel);

                // uncomment below line will enable the App to issue push notification upon receiving messages
                //showNotification(data);

                showInActivity(messageBody);

                break;

            case Constant.IMMEDIATE_ACKNOWLEDGEMENT:
                long ack1 = Long.parseLong(messageWrapper.getMessageBody());
                Log.d(TAG, "received ack: " + ack1);

                break;

            case Constant.ROUTING_ACKNOWLEDGEMENT:
                break;
        }
    }

    /**
     * handle data push out request.
     * If the sender is the server, pub to all client.
     * If the sender is client, only can send to the server.
     */
    private void onPushOutData(String data) {
        connectionManager.pushOutData(data);
    }

    /**
     * post send msg to service to handle it in background.
     */
    public static void pushOutMessage(String formattedString) {
        Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
        msg.what = MSG_PUSHOUT_DATA;
        msg.obj = formattedString;
        ConnectionService.getInstance().getHandler().sendMessage(msg);
    }

    /**
     * show the message in activity
     */
    private void showInActivity(final String msg) {
        if (chatActivity != null) {
            chatActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    chatActivity.showMessage(msg);
                }
            });
        } else {
            if (((WiFiChatApp) getApplication()).mHomeActivity != null) {
                ((WiFiChatApp) getApplication()).mHomeActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((WiFiChatApp) getApplication()).mHomeActivity.startChatActivity(msg);
                    }
                });
            }
        }
    }

    /**
     * send a notification upon receiving data
     */
    public void showNotification(String msg) {
        MessageRow row = MessageRow.parseMsgRow(msg);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.drawable.ic_action_discover, row.getMsg(), System.currentTimeMillis());
//    	notification.defaults |= Notification.DEFAULT_VIBRATE;
        CharSequence title = row.getSender();
        CharSequence text = row.getMsg();

        Intent notificationIntent = new Intent(this, WiFiDirectActivity.class);
        // pendingIntent that will start a new activity.
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(this, title, text, contentIntent);
        notificationManager.notify(1, notification);
    }
}
