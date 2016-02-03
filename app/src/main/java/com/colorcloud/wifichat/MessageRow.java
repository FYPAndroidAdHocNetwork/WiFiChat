package com.colorcloud.wifichat;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * Created by wangqilin on 5/10/15.
 */
public class MessageRow implements Parcelable {

    private static final String MESSAGE_ROW_TOKEN = "^&^";

    private String sender;
    private String msg;
    private String time;

    private MessageRow() {
        this.sender = null;
        this.time = null;
        this.msg = null;
    }

    public MessageRow(String sender, String msg, String time) {
        if (time == null) {
            Date now = new Date();
            this.time = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").format(now);
        } else {
            this.time = time;
        }

        this.sender = sender;
        this.msg = msg;
    }

    public String getSender() {
        return sender;
    }

    public String getMsg() {
        return msg;
    }

    public String getTime() {
        return time;
    }

    public MessageRow(Parcel in) {
        readFromParcel(in);
    }

    public String toString() {
        return sender + MESSAGE_ROW_TOKEN + msg + MESSAGE_ROW_TOKEN + time;
    }

    public static MessageRow parseMsgRow(String formattedMsg) {
        StringTokenizer st = new StringTokenizer(formattedMsg, MESSAGE_ROW_TOKEN);
        MessageRow row = new MessageRow();
        while (st.hasMoreTokens()) {
            // Order matters
            if (row.sender == null) {
                row.sender = st.nextToken();
                continue;
            }
            if (row.msg == null) {
                row.msg = st.nextToken();
                continue;
            }
            if (row.time == null) {
                row.time = st.nextToken();
                break;  // done
            }
        }
        return row;
    }

    public static final Parcelable.Creator<MessageRow> CREATOR = new Parcelable.Creator<MessageRow>() {
        public MessageRow createFromParcel(Parcel in) {
            return new MessageRow(in);
        }

        public MessageRow[] newArray(int size) {
            return new MessageRow[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(sender);
        dest.writeString(msg);
        dest.writeString(time);
    }

    public void readFromParcel(Parcel in) {
        sender = in.readString();
        msg = in.readString();
        time = in.readString();
    }
}