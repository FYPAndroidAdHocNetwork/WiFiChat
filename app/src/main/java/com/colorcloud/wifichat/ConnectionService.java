package com.colorcloud.wifichat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.nio.channels.SocketChannel;

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

    private static final String TAG = "PTP_Serv";

    private static ConnectionService _sinstance = null;

    private WorkHandler mWorkHandler;
    private MessageHandler mHandler;

    ChatActivity mActivity;    // shall I use weak reference here ?
    ConnectionManager mConnMan;

    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        _initialize();
    }

    private void _initialize() {
        if (_sinstance != null) {
            Log.d(TAG, "_initialize, already initialized, do nothing.");
            return;
        }

        _sinstance = this;
        mWorkHandler = new WorkHandler(TAG);
        mHandler = new MessageHandler(mWorkHandler.getLooper());

        mConnMan = new ConnectionManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        _initialize();
        return START_STICKY;
    }

    public static ConnectionService getInstance() {
        return _sinstance;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onDestroy() {
    }

    public Handler getHandler() {
        return mHandler;
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
    private void processMessage(android.os.Message msg) {
//        Log.d(TAG, "processMessage: " + msg.what);
        switch (msg.what) {
            case MSG_NULL:
                break;
            case MSG_REGISTER_ACTIVITY:
                onActivityRegister((ChatActivity) msg.obj, msg.arg1);
                break;
            case MSG_STARTSERVER:
                mConnMan.startServerSelector();
                break;
            case MSG_STARTCLIENT:
                mConnMan.startClientSelector((String) msg.obj);
                break;
            case MSG_NEW_CLIENT:
                mConnMan.onNewClient((SocketChannel) msg.obj);
                break;
            case MSG_FINISH_CONNECT:
                mConnMan.onFinishConnect((SocketChannel) msg.obj);
                break;
            case MSG_PULLIN_DATA:
                onPullInData((SocketChannel) msg.obj, msg.getData());
                break;
            case MSG_PUSHOUT_DATA:
                onPushOutData((String) msg.obj);
                break;
            case MSG_SELECT_ERROR:
                mConnMan.onSelectorError();
                break;
            case MSG_BROKEN_CONN:
                mConnMan.onBrokenConn((SocketChannel) msg.obj);
                break;
            default:
                break;
        }
    }

    /**
     * register the activity that uses this service.
     */
    private void onActivityRegister(ChatActivity activity, int register) {
        Log.d(TAG, "onActivityRegister : activity register itself to service : " + register);
        if (register == 1) {
            mActivity = activity;
        } else {
            Log.e("######", "ConnectionService - mActivity is null");
            mActivity = null;    // set to null explicitly to avoid mem leak.
        }
    }

    /**
     * service handle data in come from socket channel
     */
    private void onPullInData(SocketChannel schannel, Bundle b) {
        String data = b.getString("DATA");

        // if the senderId is myAddr, then it is an ACK, otherwise it's data msg, send ACK
        MsgRow msgRow = MsgRow.parseMsgRow(data);
        String sender = msgRow.getSender();
        String myAddr = ConnectionManager.getMyAddr();

        if (sender.equals(myAddr)) {
            // the incoming message is an ACK
            SendingMessageQueue.getInstance().acknowledge(msgRow);
            SendingMessageQueue.getInstance().printMessageQueue();
        } else {
            // the incoming message is data msg
            String time = msgRow.getTime();
            MsgRow ack = new MsgRow(sender, null, time);
            onPushOutData(ack.toString());
            Log.d("######", "ConnectionService - onPullInData, ACK sent");

            mConnMan.onDataIn(schannel, data);  // pub to all client if this device is server.
            showNotification(data);
            showInActivity(data);
        }
    }

    /**
     * handle data push out request.
     * If the sender is the server, pub to all client.
     * If the sender is client, only can send to the server.
     */
    private void onPushOutData(String data) {
        // enqueue the sending msg here, the sending msg will be acknowledged
        // TODO: in future, as more clients come in, the logics need to be changed, so far is for 1-to-1 only
        MsgRow msgRow = MsgRow.parseMsgRow(data);
        String sender = msgRow.getSender();
        // we use senderId to differentiate a data msg from an ACK
        // if senderId != MyAddr, then it's an ACK
        String myAddr = ConnectionManager.getMyAddr();
        if (sender.equals(myAddr)) {
            SendingMessageQueue.getInstance().addToSendingMessageQueue(msgRow);
            SendingMessageQueue.getInstance().printMessageQueue();
        }

        mConnMan.pushOutData(data);
    }

    /**
     * sync call to send data using conn man's channel, as conn man now is blocking on select
     */
    public int connectionSendData(String jsonstring) {
        Log.d(TAG, "connectionSendData : " + jsonstring);
        new SendDataAsyncTask(mConnMan, jsonstring).execute();
        return 0;
        //return mConnMan.clientSendData(jsonstring);
    }

    /**
     * write data in an async task to avoid NetworkOnMainThreadException.
     */
    public class SendDataAsyncTask extends AsyncTask<Void, Void, Integer> {
        private String data;
        private ConnectionManager connman;

        public SendDataAsyncTask(ConnectionManager conn, String jsonstring) {
            connman = conn;
            data = jsonstring;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            return connman.pushOutData(data);
        }

        @Override
        protected void onPostExecute(Integer result) {
            Log.d(TAG, "SendDataAsyncTask : onPostExecute:  " + data + " len: " + result);
        }
    }

    /**
     * show the message in activity
     */
    private void showInActivity(final String msg) {
        Log.d(TAG, "showInActivity : " + msg);
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.showMessage(msg);
                }
            });
        } else {
            Log.d(TAG, "showInActivity :  chat activity down, force start !");
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
     * send a notification upon recv data
     */
    public void showNotification(String msg) {
        MsgRow row = MsgRow.parseMsgRow(msg);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.drawable.ic_action_discover, row.getMsg(), System.currentTimeMillis());
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        CharSequence title = row.getSender();
        CharSequence text = row.getMsg();

        Intent notificationIntent = new Intent(this, WiFiDirectActivity.class);
        // pendingIntent that will start a new activity.
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(this, title, text, contentIntent);
        notificationManager.notify(1, notification);
    }
}
