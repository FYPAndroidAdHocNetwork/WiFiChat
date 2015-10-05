package com.colorcloud.wifichat;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * this class encapsulate the NIO buffer and NIO channel on top of socket. It is all abt NIO style.
 * SSLServerSocketChannel, ServerSocketChannel, SocketChannel, Selector, ByteBuffer, etc.
 * NIO buffer (ByteBuffer) either in writing mode or in reading mode. Need to flip the mode before reading or writing.
 * <p/>
 * You know when a socket channel disconnected when you read -1 or write exception. You need app level ACK.
 */
public class ConnectionManager {

    private final String TAG = "ConnectionManager";

    private Context mContext;
    ConnectionService mService;
    // Server knows all clients. key is ip addr, value is socket channel.
    // when remote client screen on, a new connection with the same ip addr is established.
    private Map<String, SocketChannel> mClientChannels = new HashMap<String, SocketChannel>();

    // global selector and channels
    private boolean mIsServer = false;
    private Selector mSelector = null;
    private ServerSocketChannel mServerSocketChannel = null;
    private SocketChannel mClientSocketChannel = null;
    String mClientAddr = null;
    String mServerAddr = null;

    /**
     * constructor
     */
    public ConnectionManager(ConnectionService service) {
        mService = service;
    }

    public void configIPV4() {
        // by default Selector attempts to work on IPv6 stack.
        java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    /**
     * create a server socket channel to listen to the port for incoming connections.
     */
    public static ServerSocketChannel createServerSocketChannel(int port) throws IOException {
        // Create a non-blocking socket channel
        ServerSocketChannel ssChannel = ServerSocketChannel.open();
        ssChannel.configureBlocking(false);
        ServerSocket serverSocket = ssChannel.socket();
        serverSocket.bind(new InetSocketAddress(port));  // bind to the port to listen.
        return ssChannel;
    }

    /**
     * Creates a non-blocking socket channel to connect to specified host name and port.
     * connect() is called on the new channel before it is returned.
     */
    public static SocketChannel createSocketChannel(String hostName, int port) throws IOException {
        // Create a non-blocking socket channel
        SocketChannel sChannel = SocketChannel.open();
        sChannel.configureBlocking(false);

        // Send a connection request to the server; this method is non-blocking
        sChannel.connect(new InetSocketAddress(hostName, port));
        return sChannel;
    }


    /**
     * create a socket channel and connect to the host.
     * after return, the socket channel guarantee to be connected.
     */
    public SocketChannel connectTo(String hostname, int port) {
        SocketChannel sChannel = null;
        try {
            sChannel = createSocketChannel(hostname, port);  // connect to the remote host, port

            // Before the socket is usable, the connection must be completed. finishConnect().
            while (!sChannel.finishConnect()) {
                // blocking spin lock
            }
            // Socket channel is now ready to use
        } catch (IOException e) {
            Log.e(TAG, "connectTo: exception : " + e.toString());
            //Toast.makeText(this.mContext, "connectTo: exception : ",  Toast.LENGTH_SHORT).show();
            return null;
        }
//        Log.d(TAG, "connectTo: finishConnect : ");
        //Toast.makeText(this.mContext,"connectTo: finishConnect : ",  Toast.LENGTH_SHORT).show();
        return sChannel;
    }

    /**
     * client, after p2p connection available, connect to group owner and select monitoring the sockets.
     * start blocking selector monitoring in an async task, infinite loop
     */
    public int startClientSelector(String host) {
        closeServer();   // close linger server.

        if (mClientSocketChannel != null) {
//            Log.d(TAG, "startClientSelector : client already connected to server: " + mClientSocketChannel.socket().getLocalAddress().getHostAddress());
            //Toast.makeText(this.mContext, "startClientSelector : client already connected to server: " + mClientSocketChannel.socket().getLocalAddress().getHostAddress(),  Toast.LENGTH_SHORT).show();
            return -1;
        }

        try {
            // connected to the server upon start client.
            SocketChannel sChannel = connectTo(host, 1080);
            if (sChannel == null) {
                return -1;
            }

            mSelector = Selector.open();
            mClientSocketChannel = sChannel;
            mClientAddr = mClientSocketChannel.socket().getLocalAddress().getHostName();
            sChannel.register(mSelector, SelectionKey.OP_READ);
            ((WiFiChatApp) mService.getApplication()).setMyAddr(mClientAddr);
//            Log.d(TAG, "startClientSelector : started: " + mClientSocketChannel.socket().getLocalAddress().getHostAddress());

            //Toast.makeText(this.mContext,"startClientSelector : started: " + mClientSocketChannel.socket().getLocalAddress().getHostAddress(),  Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "startClientSelector : exception: ");

            //Toast.makeText(this.mContext,"startClientSelector : exception: ",  Toast.LENGTH_SHORT).show();
            mSelector = null;
            mClientSocketChannel = null;
            return -1;
        }

        // start selector monitoring, blocking
        new SelectorAsyncTask(mService, mSelector).execute();
        return 0;
    }

    /**
     * create a selector to manage a server socket channel
     * The registration process yields an object called a selection key which identifies the selector/socket channel pair
     */
    public int startServerSelector() {
        closeClient();   // close linger client, if exists.

        try {
            // create server socket and register to selector to listen OP_ACCEPT event
            ServerSocketChannel sServerChannel = createServerSocketChannel(1080); // BindException if already bind.
            mServerSocketChannel = sServerChannel;
            mServerAddr = mServerSocketChannel.socket().getInetAddress().getHostAddress();
            if ("0.0.0.0".equals(mServerAddr)) {
                mServerAddr = "Header";
            }
            ((WiFiChatApp) mService.getApplication()).setMyAddr(mServerAddr);

            mSelector = Selector.open();
            SelectionKey acceptKey = sServerChannel.register(mSelector, SelectionKey.OP_ACCEPT);
            acceptKey.attach("accept_channel");
            mIsServer = true;

            //SocketChannel sChannel = createSocketChannel("hostname.com", 80);
            //sChannel.register(selector, SelectionKey.OP_CONNECT);  // listen to connect event.
//            Log.d(TAG, "startServerSelector : started: " + sServerChannel.socket().getLocalSocketAddress().toString());
            //Toast.makeText(this.mContext,"startServerSelector : started: " + sServerChannel.socket().getLocalSocketAddress().toString(),  Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "startServerSelector : exception: ");
            //Toast.makeText(this.mContext,"startServerSelector : exception: ",  Toast.LENGTH_SHORT).show();
            return -1;
        }

        new SelectorAsyncTask(mService, mSelector).execute();
        return 0;
    }

    /**
     * handle selector error, re-start
     */
    public void onSelectorError() {
        Log.e(TAG, " onSelectorError : do nothing for now.");
        //Toast.makeText(this.mContext," onSelectorError : do nothing for now.",  Toast.LENGTH_SHORT).show();
        // new SelectorAsyncTask(mService, mSelector).execute();
    }

    /**
     * a device can only be either group owner, or group client, not both.
     * when we start as client, close server, if existing due to linger connection.
     */
    private void closeServer() {
        if (mServerSocketChannel != null) {
            try {
                mServerSocketChannel.close();
                mSelector.close();
            } catch (Exception e) {

            } finally {
                mIsServer = false;
                mServerSocketChannel = null;
                mSelector = null;
                mServerAddr = null;
                mClientChannels.clear();
            }
        }
    }

    private void closeClient() {
        if (mClientSocketChannel != null) {
            try {
                mClientSocketChannel.close();
                mSelector.close();
            } catch (Exception e) {

            } finally {
                mClientSocketChannel = null;
                mSelector = null;
                mClientAddr = null;
            }
        }
    }

    /**
     * read out -1, connection broken, remove it from clients collection
     */
    public void onBrokenConn(SocketChannel schannel) {
        String peeraddr = schannel.socket().getInetAddress().getHostAddress();
        if (mIsServer) {
            mClientChannels.remove(peeraddr);
//            Log.d(TAG, "onBrokenConn : client down: " + peeraddr);
            //Toast.makeText(this.mContext,"onBrokenConn : client down: " + peeraddr,  Toast.LENGTH_SHORT).show();
        } else {
//            Log.d(TAG, "onBrokenConn : set null client channel after server down: " + peeraddr);
            //Toast.makeText(this.mContext,"onBrokenConn : set null client channel after server down: " + peeraddr,  Toast.LENGTH_SHORT).show();
            mClientSocketChannel = null;
        }
    }

    /**
     * Server handle new client coming in.
     */
    public void onNewClient(SocketChannel schannel) {
        String ipaddr = schannel.socket().getInetAddress().getHostAddress();
//        Log.d(TAG, "onNewClient : server added remote client: " + ipaddr);
        //Toast.makeText(this.mContext,"onNewClient : server added remote client: " + ipaddr,  Toast.LENGTH_SHORT).show();
        mClientChannels.put(ipaddr, schannel);
    }

    /**
     * Client's connect to server success,
     */
    public void onFinishConnect(SocketChannel schannel) {
        String clientaddr = schannel.socket().getLocalAddress().getHostAddress();
        String serveraddr = schannel.socket().getInetAddress().getHostAddress();
//        Log.d(TAG, "onFinishConnect : client connect to server succeed : " + clientaddr + " -> " + serveraddr);
        //Toast.makeText(this.mContext,"onFinishConnect : client connect to server succeed : " + clientaddr + " -> " + serveraddr,  Toast.LENGTH_SHORT).show();
        mClientSocketChannel = schannel;
        mClientAddr = clientaddr;
        ((WiFiChatApp) mService.getApplication()).setMyAddr(mClientAddr);
    }

    /**
     * client send data into server, server pub to all clients.
     */
    public void onDataIn(SocketChannel schannel, String data) {
        Log.d("######", "ConnectionManager - onDataIn:" + data);



        //Toast.makeText(this.mContext,"connection onDataIn : " + data,  Toast.LENGTH_SHORT).show();
        if (mIsServer) {  // push all _other_ clients if the device is the server
            pubDataToAllClients(data, schannel);
        }
    }

    /**
     * write byte buf to the socket channel.
     */
    private int writeData(SocketChannel sChannel, String jsonString) {
        byte[] buf = jsonString.getBytes();
        ByteBuffer bytebuf = ByteBuffer.wrap(buf);  // wrap the buf into byte buffer
        int nwritten = 0;
        try {
            //bytebuf.flip();  // no flip after creating from wrap.
//            Log.d(TAG, "writeData: start:limit = " + bytebuf.position() + " : " + bytebuf.limit());
            //Toast.makeText(this.mContext,"writeData: start:limit = " + bytebuf.position() + " : " + bytebuf.limit(),  Toast.LENGTH_SHORT).show();
            nwritten = sChannel.write(bytebuf);
        } catch (Exception e) {
            // Connection may have been closed
            Log.e(TAG, "writeData: exception : " + e.toString());
            //Toast.makeText(this.mContext,"writeData: exception : ",  Toast.LENGTH_SHORT).show();
            onBrokenConn(sChannel);
        }
//        Log.d(TAG, "writeData: content: " + new String(buf) + "  : len: " + nwritten);
        //Toast.makeText(this.mContext, "writeData: content: " + new String(buf) + "  : len: " + nwritten,  Toast.LENGTH_SHORT).show();
        return nwritten;
    }

    /**
     * server publish data to all the connected clients
     */
    private void pubDataToAllClients(String msg, SocketChannel incomingChannel) {
        Log.d("######", "ConnectionManager - pubDataToAllClients : isServer: " + mIsServer + ", msg: " + msg);
        //Toast.makeText(this.mContext,"pubDataToAllClients : isServer ? " + mIsServer + " msg: " + msg ,  Toast.LENGTH_SHORT).show();
        if (!mIsServer) {
//            Log.d(TAG, "pubDataToAllClients : not server");
            return;
        }

        for (SocketChannel s : mClientChannels.values()) {
            if (s != incomingChannel) {
                String peeraddr = s.socket().getInetAddress().getHostAddress();
//                Log.d(TAG, "pubDataToAllClients : Server pub data to:  " + peeraddr);
                //Toast.makeText(this.mContext,"pubDataToAllClients : Server pub data to:  " + peeraddr,  Toast.LENGTH_SHORT).show();
                writeData(s, msg);
            }
            String peeraddr = s.socket().getInetAddress().getHostAddress();
//            Log.d(TAG, "pubDataToAllClients : Server pub data to:  " + peeraddr);
        }
//        Log.d(TAG, "pubDataToAllClients : end s : " + mClientChannels.values().size());
    }

    /**
     * the device want to push out data.
     * If the device is client, the only channel is to the server.
     * If the device is server, it just pub the data to all clients for now.
     */
    public int pushOutData(String jsonString) {
        Log.d("######", "ConnectionManager - pushOutData: " + jsonString);
        if (!mIsServer) {   // device is client, can only send to server
            sendDataToServer(jsonString);
        } else {
            // server pub to all clients, msg already appended with sender addr inside send button handler.
            pubDataToAllClients(jsonString, null);
        }
        return 0;
    }

    /**
     * whenever client write to server, carry the format of "client_addr : msg "
     */
    private int sendDataToServer(String jsonString) {
        Log.d("######", "ConnectionManager - sendDataToServer: " + jsonString);
        if (mClientSocketChannel == null) {
//            Log.d(TAG, "sendDataToServer: channel not connected ! waiting...");
            //Toast.makeText(this.mContext,"sendDataToServer: channel not connected ! waiting...",  Toast.LENGTH_SHORT).show();
            return 0;
        }
//        Log.d(TAG, "sendDataToServer: " + mClientAddr + " -> " + mClientSocketChannel.socket().getInetAddress().getHostAddress() + " : " + jsonString);
        //Toast.makeText(this.mContext,"sendDataToServer: " + mClientAddr + " -> " + mClientSocketChannel.socket().getInetAddress().getHostAddress() + " : " +  jsonString,  Toast.LENGTH_SHORT).show();
        return writeData(mClientSocketChannel, jsonString);
    }
}
