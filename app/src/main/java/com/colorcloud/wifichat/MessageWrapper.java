package com.colorcloud.wifichat;

import android.util.Log;

import java.util.StringTokenizer;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static com.colorcloud.wifichat.Constant.MESSAGE;
import static com.colorcloud.wifichat.Constant.MESSAGE_WRAPPER_TOKEN;

/**
 * Created by wangqilin on 1/2/16.
 */
public class MessageWrapper {
    private static final String TAG = "MessageWrapper";

    private int category;
    private long ack;
    private String messageBody;

    private MessageWrapper() {
        this.category = 0; // which doesn't belong to any category
        this.ack = 0;
        this.messageBody = "";
    }

    public MessageWrapper(int category, String messageBody) {
        this.category = category;
        this.messageBody = messageBody;

//         ACK is only a meaningful field with the type MESSAGE
        if (category == MESSAGE) {
            byte bytes[] = messageBody.getBytes();
            Checksum checksum = new CRC32();
            checksum.update(bytes, 0, bytes.length);
            this.ack = checksum.getValue();
            // TODO: 6/2/16 when a MESSAGE is constructed, it should be enqueued in the message queue for ACK
        } else {
            this.ack = 0;
        }
    }

    public int getCategory() {
        return this.category;
    }

    public long getAck() {
        return this.ack;
    }

    public String getMessageBody() {
        return this.messageBody;
    }

    public String toString() {
        return category + MESSAGE_WRAPPER_TOKEN + ack + MESSAGE_WRAPPER_TOKEN + messageBody;
    }

    public static MessageWrapper parseMessageWrapper(String string) {
        int parsingProgress = 0; // parsing has 3 stages 1,2,3
        StringTokenizer stringTokenizer = new StringTokenizer(string, MESSAGE_WRAPPER_TOKEN);
        MessageWrapper messageWrapper = new MessageWrapper();

        while (stringTokenizer.hasMoreTokens()) {
            if (parsingProgress == 0) {
                messageWrapper.category = Integer.parseInt(stringTokenizer.nextToken());
                parsingProgress++;
                continue;
            }
            if (parsingProgress == 1) {
                messageWrapper.ack = Long.parseLong(stringTokenizer.nextToken());
                parsingProgress++;
                continue;
            }
            if (parsingProgress == 2) {
                messageWrapper.messageBody = stringTokenizer.nextToken();
                break;
            }
        }

        return messageWrapper;
    }
}
