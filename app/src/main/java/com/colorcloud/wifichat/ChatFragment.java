package com.colorcloud.wifichat;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * chat fragment attached to main activity.
 */
public class ChatFragment extends ListFragment {
    private static final String TAG = "ChatFragment";

    private static ChatActivity mActivity = null;

    private ArrayList<MessageRow> mMessageList = null;   // a list of chat msgs.
    private ArrayAdapter<MessageRow> mAdapter = null;

    private String myDeviceName;

    /**
     * Static factory to create a fragment object from tab click.
     */
    public static ChatFragment newInstance(Activity activity, String groupOwnerAddr, String msg) {
        ChatFragment f = new ChatFragment();
        mActivity = (ChatActivity) activity;

        Bundle args = new Bundle();
        args.putString("groupOwnerAddr", groupOwnerAddr);
        args.putString("initMsg", msg);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {  // this callback invoked after newInstance done.  
        super.onCreate(savedInstanceState);
        myDeviceName = ((WiFiChatApp) mActivity.getApplication()).mHomeActivity.myDevice.deviceName;

        setRetainInstance(true);   // Tell the framework to try to keep this fragment around during a configuration change.
    }

    /**
     * the data you place in the Bundle here will be available in the Bundle given to onCreate(Bundle), etc.
     * only works when your activity is destroyed by android platform. If the user closed the activity, no call of this.
     * http://www.eigo.co.uk/Managing-State-in-an-Android-Activity.aspx
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("MSG_LIST", mMessageList);
    }

    /**
     * no matter your fragment is declared in main activity layout, or dynamically added thru fragment transaction
     * You need to inflate fragment view inside this function.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // inflate the fragment's res layout.
        View contentView = inflater.inflate(R.layout.chat_frag, container, false);  // no care whatever container is.
        final EditText inputEditText = (EditText) contentView.findViewById(R.id.edit_input);
        final Button sendBtn = (Button) contentView.findViewById(R.id.btn_send);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // send the chat text in current line to the server
                String inputMsg = inputEditText.getText().toString();
                if (!inputMsg.equals("")) {
                    inputEditText.setText("");
                    InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);

                    MessageRow messageRow = new MessageRow(myDeviceName, inputMsg, null);
                    MessageWrapper messageWrapper = new MessageWrapper(Constant.MESSAGE, messageRow.toString());
                    appendChatMessage(messageRow);
                    final String formattedMsg = messageWrapper.toString();

                    //todo: multi-hop state has different behaviors
                    if (WiFiDirectActivity.multihopState == false) {
                        SendingMessageQueue.getInstance().addToSendingMessageQueue(messageWrapper.getAck(), messageWrapper);
                        ConnectionService.pushOutMessage(formattedMsg);
                    } else {
                        Log.d(TAG, "multi-hop flow");
                        RoutingManager.getInstance().connectionTest(formattedMsg);
                    }
                } else
                    Toast.makeText(getActivity(), "Please input text", Toast.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState != null) {
            mMessageList = savedInstanceState.getParcelableArrayList("MSG_LIST");
        } else if (mMessageList == null) {
            // no need to setContentView, just setListAdapter, but listview must be android:id="@android:id/list"
            mMessageList = new ArrayList<MessageRow>(200);
        }

        mAdapter = new ChatMessageAdapter(mActivity, mMessageList);
        String initmsg = getArguments().getString("initMsg");

        if (initmsg != null) {
            MessageRow row = MessageRow.parseMsgRow(initmsg);
            mMessageList.add(row);
        } else if (mMessageList.size() == 0) {
            mMessageList.add(new MessageRow(myDeviceName, myDeviceName + " logged in", null));
        }
        setListAdapter(mAdapter);  // list fragment data adapter 

        return contentView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {  // invoked after fragment view created.
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    /**
     * add a chat message to the list, return the format the message as " sender_addr : msg "
     */
    public void appendChatMessage(MessageRow msg) {
        mMessageList.add(msg);
        mAdapter.notifyDataSetChanged();  // notify the attached observer and views to refresh.
    }

    /**
     * chat message adapter from list adapter.
     * Responsible for how to show data to list fragment list view.
     */
    final class ChatMessageAdapter extends ArrayAdapter<MessageRow> {

        private LayoutInflater mInflater;

        public ChatMessageAdapter(Context context, List<MessageRow> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getItemViewType(int position) {
            return IGNORE_ITEM_VIEW_TYPE;   // do not care
        }

        /**
         * assemble each row view in the list view.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;  // old view to re-use if possible. Useful for Heterogeneous list with diff item view type.
            MessageRow item = this.getItem(position);

            if (view == null) {
                view = mInflater.inflate(R.layout.chat_row, null);
            }

            TextView sender = (TextView) view.findViewById(R.id.sender);
            sender.setText(item.getSender());

            TextView msgRow = (TextView) view.findViewById(R.id.msg_row);
            msgRow.setText(item.getMsg());

            TextView time = (TextView) view.findViewById(R.id.time);
            time.setText(item.getTime());

            // set the background color
            if (!item.getSender().equals(myDeviceName)) {
                // view.setBackgroundResource(R.drawable.row_bkgrd);
                msgRow.setTextColor(Color.GREEN);
                sender.setTextColor(Color.GREEN);
            } else {
                msgRow.setTextColor(Color.CYAN);
                sender.setTextColor(Color.CYAN);
            }
            time.setTextColor(Color.WHITE);

            return view;
        }
    }
}
