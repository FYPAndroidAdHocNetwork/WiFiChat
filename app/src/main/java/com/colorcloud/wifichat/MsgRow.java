package com.colorcloud.wifichat;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * Created by wangqilin on 5/10/15.
 */
public class MsgRow implements Parcelable {

    private static final String STRING_TOKEN = "^&^";

    private String sender;
    private String msg;
    private String time;

    private MsgRow() {
        this.sender = null;
        this.time = null;
        this.msg = null;
    }

    public MsgRow(String sender, String msg) {
        Date now = new Date();
        time = new SimpleDateFormat("dd-M-yyyy hh:mm:ss").format(now);
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

    public MsgRow(Parcel in) {
        readFromParcel(in);
    }

    public String toString() {
        return sender + STRING_TOKEN + msg + STRING_TOKEN + time;
    }

    public static MsgRow parseMsgRow(String formattedMsg) {
        StringTokenizer st = new StringTokenizer(formattedMsg, STRING_TOKEN);
        MsgRow row = new MsgRow();
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

    public static final Parcelable.Creator<MsgRow> CREATOR = new Parcelable.Creator<MsgRow>() {
        public MsgRow createFromParcel(Parcel in) {
            return new MsgRow(in);
        }

        public MsgRow[] newArray(int size) {
            return new MsgRow[size];
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
