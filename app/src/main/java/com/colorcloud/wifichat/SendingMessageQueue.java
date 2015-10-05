package com.colorcloud.wifichat;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by wangqilin on 5/10/15.
 */
public class SendingMessageQueue {
    private static SendingMessageQueue instance;
    private Queue<MsgRow> messageQueue;

    private SendingMessageQueue() {
        messageQueue = new LinkedList<MsgRow>();
    }

    // thread-safe singleton implementation
    public static synchronized SendingMessageQueue getInstance() {
        if (instance == null) {
            instance = new SendingMessageQueue();
        }
        return instance;
    }

    public void addToSendingMessageQueue(MsgRow msgRow) {
        messageQueue.add(msgRow);
        Log.d("######", "SendingMessageQueue - addToSendingMessageQueue: time: " + msgRow.getTime() + " sender: " + msgRow.getSender());
    }

    public boolean acknowledge(MsgRow msgRow) {
        boolean result = false;

        String sender = msgRow.getSender();
        String time = msgRow.getTime();

        for (MsgRow storedMsgRow : messageQueue) {
            if (sender.equals(storedMsgRow.getSender()) && time.equals(storedMsgRow.getTime())) {
                messageQueue.remove(storedMsgRow);
                result = true;
                Log.d("######", "SendingMessageQueue - msg from: " + sender + " at time: " + time + " was ACK successfully!");
            }
        }

        return result;
    }

    // for debug purpose
    public void printMessageQueue() {
        Log.d("######", "SendingMessageQueue - length of the message queue: " + messageQueue.size());

        for (MsgRow storedMsgRow : messageQueue) {
            Log.d("######", "SendingMessageQueue:  msg from: " + storedMsgRow.getSender() + " at time: " + storedMsgRow.getTime());
        }
    }
}
