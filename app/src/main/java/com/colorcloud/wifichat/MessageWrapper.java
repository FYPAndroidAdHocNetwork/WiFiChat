package com.colorcloud.wifichat;

import java.util.StringTokenizer;

/**
 * Created by wangqilin on 1/2/16.
 */
public class MessageWrapper {

    private static final String MESSAGE_WRAPPER_TOKEN = "#$#";

    private int category;
    private String messageBody;

    private MessageWrapper() {
        this.category = 0; // which doesn't belong to any category
        this.messageBody = null;
    }

    public MessageWrapper(int category, String messageBody) {
        this.category = category;
        this.messageBody = messageBody;
    }

    public int getCategory() {
        return this.category;
    }

    public String getMessageBody() {
        return this.messageBody;
    }

    public String toString() {
        return category + MESSAGE_WRAPPER_TOKEN + messageBody;
    }

    public static MessageWrapper parseMessageWrapper(String string) {
        StringTokenizer stringTokenizer = new StringTokenizer(string, MESSAGE_WRAPPER_TOKEN);
        MessageWrapper messageWrapper = new MessageWrapper();

        while (stringTokenizer.hasMoreTokens()) {
            if (messageWrapper.category == 0) {
                messageWrapper.category = Integer.parseInt(stringTokenizer.nextToken());
                continue;
            }
            if (messageWrapper.messageBody == null) {
                messageWrapper.messageBody = stringTokenizer.nextToken();
                break;
            }
        }

        return messageWrapper;
    }

}
