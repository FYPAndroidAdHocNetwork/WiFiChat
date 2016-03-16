package com.colorcloud.wifichat;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wangqilin on 5/10/15.
 */
public class SendingMessageQueue {
    private static final String TAG = "SendingMessageQueue";

    private static SendingMessageQueue instance;
    private Map<Long, MessageWrapper> messageQueue;

    private SendingMessageQueue() {
        messageQueue = new HashMap<Long, MessageWrapper>();
    }

    // thread-safe singleton implementation
    public static synchronized SendingMessageQueue getInstance() {
        if (instance == null) {
            instance = new SendingMessageQueue();
        }
        return instance;
    }

    public void addToSendingMessageQueue(Long ack, MessageWrapper messageWrapper) {
        messageQueue.put(ack, messageWrapper);
        Log.d(TAG, "message added to the queue with an ack: " + ack);
    }

    public void acknowledge(long ack) {
        messageQueue.remove(ack);
        Log.d(TAG, "message with the ack: " + ack + " has been acknowledged");
    }

    public void reset() {
        messageQueue.clear();
        Log.d(TAG, "message queue has been cleared");
    }

    // for debug purpose
    public void printMessageQueue() {
        Log.d(TAG, "size of the message queue: " + messageQueue.size());
        for (MessageWrapper messageWrapper: messageQueue.values()) {
            Log.d(TAG, messageWrapper.toString());
        }
    }
}