package com.colorcloud.wifichat;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import static com.colorcloud.wifichat.Constant.MSG_REGISTER_ACTIVITY;

public class ChatActivity extends Activity {

    public static final String TAG = "ChatActivity";
    ChatFragment chatFrag = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.chat);
            Intent i = getIntent();
            String initMsg = i.getStringExtra("FIRST_MSG");
            initFragment(initMsg);
        } catch (Exception e) {
            Toast.makeText(this, "On Create Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * init fragment with possible recvd start up message.
     */
    public void initFragment(String initMsg) {
        try {
            // to add fragments to your activity layout, just specify which viewgroup to place the fragment.
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            if (chatFrag == null) {
                chatFrag = ChatFragment.newInstance(this, null, initMsg);
            }

            // chat fragment on top, do not do replace, as frag_detail already hard coded in layout.
            ft.add(R.id.frag_chat, chatFrag, "chat_frag");
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(this, "Init fragment Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        try {
            super.onResume();
            registerActivityToService(true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(this, "On Resume Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPause() {
        try {
            super.onPause();
            registerActivityToService(false);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(this, "ChatActivity onPause Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        try {
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(this, "ChatActivity onDestroy Failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * set listview weight works, only when layout has only one LinearLayout.
     */
    @Deprecated
    private void testWithListViewWeight() {
        try {
            List<String> mMessageList;   // a list of chat msgs.
            ArrayAdapter<String> mAdapter;

            mMessageList = new ArrayList<String>(200);
            for (int i = 0; i < 100; i++)
                mMessageList.add("User logged in");
            mAdapter = new ChatMessageAdapter(this, mMessageList);

            //setListAdapter(mAdapter);  // list fragment data adapter
            mAdapter.notifyDataSetChanged();  // notify the attached observer and views to refresh.
            Toast.makeText(this, "User logged in", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "List View Failed", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * chat message adapter from list adapter.
     * Responsible for how to show data to list fragment list view.
     */
    final class ChatMessageAdapter extends ArrayAdapter<String> {
        private LayoutInflater mInflater;

        public ChatMessageAdapter(Context context, List<String> objects) {
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
            try {
                String item = this.getItem(position);

                if (view == null) {
                    view = mInflater.inflate(R.layout.msg_row, null);
                }

                TextView msgRow = (TextView) view.findViewById(R.id.msg_row);
                msgRow.setText(item);
                Toast.makeText(this.getContext(), item + " received", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this.getContext(), "Get View Failed", Toast.LENGTH_SHORT).show();
            }
            return view;
        }
    }

    /**
     * register this activity to service process, so that service later can update list view.
     * In AsyncTask, the activity itself is passed to the constructor of asyncTask, hence later
     * onPostExecute() can do chatActivity.mCommentsAdapter.notifyDataSetChanged();
     * Just need to be careful of reference to avoid mem leak.
     */
    protected void registerActivityToService(boolean register) {
        try {
            if (ConnectionService.getInstance() != null) {
                Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
                msg.what = MSG_REGISTER_ACTIVITY;
                msg.obj = this;
                msg.arg1 = register ? 1 : 0;
                ConnectionService.getInstance().getHandler().sendMessage(msg);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * show the msg in chat fragment
     */
    public void showMessage(String msg) {
        try {
            MessageRow row = MessageRow.parseMsgRow(msg);
            if (chatFrag != null) {
                chatFrag.appendChatMessage(row);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(this, "Show Msg Failed", Toast.LENGTH_SHORT).show();
        }
    }
}
