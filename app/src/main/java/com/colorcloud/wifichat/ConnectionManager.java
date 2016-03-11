package com.colorcloud.wifichat;

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

    private static final String TAG = "ConnectionManager";

    private static final int PORT_NUMBER = 1080;

    ConnectionService connectionService;
    // Server knows all clients. key is ip addr, value is socket channel.
    // when remote client screen on, a new connection with the same ip addr is established.
    private Map<String, SocketChannel> clientChannels = new HashMap<String, SocketChannel>();

    // global selector and channels
    private boolean isServer = false;
    private Selector selector = null;
    private ServerSocketChannel serverSocketChannel = null;
    private SocketChannel clientSocketChannel = null;
    String clientAddr = null;
    String serverAddr = null;

    /**
     * constructor
     */
    public ConnectionManager(ConnectionService service) {
        connectionService = service;
    }

    /**
     * create a server socket channel to listen to the port for incoming connections.
     */
    public static ServerSocketChannel createServerSocketChannel(int port) throws IOException {
        // Create a non-blocking socket channel
        ServerSocketChannel ssChannel = ServerSocketChannel.open();
        ssChannel.configureBlocking(false);
        ServerSocket serverSocket = ssChannel.socket();
        serverSocket.setReuseAddress(true);
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
        SocketChannel sChannel;
        try {
            sChannel = createSocketChannel(hostname, port);  // connect to the remote host, port
            // Before the socket is usable, the connection must be completed. finishConnect().
            while (!sChannel.finishConnect()) {
                // blocking spin lock
            }
            // Socket channel is now ready to use
        } catch (IOException e) {
            Log.e(TAG, "connectTo: exception : " + e.toString());
            return null;
        }
        return sChannel;
    }

    /**
     * client, after p2p connection available, connect to group owner and select monitoring the sockets.
     * start blocking selector monitoring in an async task, infinite loop
     */
    public int startClientSelector(String host) {
        closeServer();   // close linger server.

        if (clientSocketChannel != null) {
            Log.d(TAG, "startClientSelector: client already connected to server: " + clientSocketChannel.socket().getLocalAddress().getHostAddress());
            return 0;
        }

        try {
            // connected to the server upon start client.
            SocketChannel sChannel = connectTo(host, PORT_NUMBER);
            if (sChannel == null) {
                Log.e(TAG, "failed to connect to server");
                return -1;
            }

            selector = Selector.open();
            clientSocketChannel = sChannel;
            clientAddr = clientSocketChannel.socket().getLocalAddress().getHostName();
            sChannel.register(selector, SelectionKey.OP_READ);
            ((WiFiChatApp) connectionService.getApplication()).setMyAddr(clientAddr);
            Log.d(TAG, "startClientSelector: started: " + clientSocketChannel.socket().getLocalAddress().getHostAddress());
        } catch (Exception e) {
            Log.e(TAG, "startClientSelector exception: " + e.toString());

            selector = null;
            clientSocketChannel = null;
            return -1;
        }

        // start selector monitoring, blocking
        new SelectorAsyncTask(connectionService, selector).execute();
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
            ServerSocketChannel sServerChannel = createServerSocketChannel(PORT_NUMBER); // BindException if already bind.
            serverSocketChannel = sServerChannel;
            serverAddr = serverSocketChannel.socket().getInetAddress().getHostAddress();
            if ("0.0.0.0".equals(serverAddr)) {
                serverAddr = "Header";
            }
            ((WiFiChatApp) connectionService.getApplication()).setMyAddr(serverAddr);

            selector = Selector.open();
            SelectionKey acceptKey = sServerChannel.register(selector, SelectionKey.OP_ACCEPT);
            acceptKey.attach("accept_channel");
            isServer = true;

            //SocketChannel sChannel = createSocketChannel("hostname.com", 80);
            //sChannel.register(selector, SelectionKey.OP_CONNECT);  // listen to connect event.
//            Log.d(TAG, "startServerSelector : started: " + sServerChannel.socket().getLocalSocketAddress().toString());
            //Toast.makeText(this.mContext,"startServerSelector : started: " + sServerChannel.socket().getLocalSocketAddress().toString(),  Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // TODO: 1/3/16 how to solve this exception?
            Log.e(TAG, "startServerSelector exception: " + e.toString());
            return -1;
        }

        new SelectorAsyncTask(connectionService, selector).execute();
        return 0;
    }

    /**
     * handle selector error, re-start
     */
    public void onSelectorError() {
        Log.e("ConnectionManager", " onSelectorError : do nothing for now.");
    }

    /**
     * a device can only be either group owner, or group client, not both.
     * when we start as client, close server, if existing due to linger connection.
     */
    private void closeServer() {
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
                selector.close();
            } catch (Exception e) {
                Log.e(TAG, "closeServer exception: " + e.toString());
            } finally {
                isServer = false;
                serverSocketChannel = null;
                selector = null;
                serverAddr = null;
                clientChannels.clear();
            }
        }
    }

    private void closeClient() {
        if (clientSocketChannel != null) {
            try {
                clientSocketChannel.close();
                selector.close();
            } catch (Exception e) {
                Log.e(TAG, "closeClient exception: " + e.toString());
            } finally {
                clientSocketChannel = null;
                selector = null;
                clientAddr = null;
            }
        }
    }

    /**
     * read out -1, connection broken, remove it from clients collection
     */
    public void onBrokenConn(SocketChannel socketChannel) {
        String peeraddr = socketChannel.socket().getInetAddress().getHostAddress();
        if (isServer) {
            clientChannels.remove(peeraddr);
//            Log.d(TAG, "onBrokenConn : client down: " + peeraddr);
            //Toast.makeText(this.mContext,"onBrokenConn : client down: " + peeraddr,  Toast.LENGTH_SHORT).show();
        } else {
//            Log.d(TAG, "onBrokenConn : set null client channel after server down: " + peeraddr);
            //Toast.makeText(this.mContext,"onBrokenConn : set null client channel after server down: " + peeraddr,  Toast.LENGTH_SHORT).show();
            clientSocketChannel = null;
        }
    }

    /**
     * Server handle new client coming in.
     */
    public void onNewClient(SocketChannel socketChannel) {
        String ipaddr = socketChannel.socket().getInetAddress().getHostAddress();
//        Log.d(TAG, "onNewClient : server added remote client: " + ipaddr);
        //Toast.makeText(this.mContext,"onNewClient : server added remote client: " + ipaddr,  Toast.LENGTH_SHORT).show();
        clientChannels.put(ipaddr, socketChannel);
    }

    /**
     * Client's connect to server success,
     */
    public void onFinishConnect(SocketChannel socketChannel) {
        String clientaddr = socketChannel.socket().getLocalAddress().getHostAddress();
        String serveraddr = socketChannel.socket().getInetAddress().getHostAddress();
//        Log.d(TAG, "onFinishConnect : client connect to server succeed : " + clientaddr + " -> " + serveraddr);
        //Toast.makeText(this.mContext,"onFinishConnect : client connect to server succeed : " + clientaddr + " -> " + serveraddr,  Toast.LENGTH_SHORT).show();
        clientSocketChannel = socketChannel;
        clientAddr = clientaddr;
        ((WiFiChatApp) connectionService.getApplication()).setMyAddr(clientAddr);
    }

    /**
     * client send data into server, server pub to all clients.
     */
    public void onDataIn(SocketChannel socketChannel, String data) {
        // push all other clients if the device is the server
        if (isServer) {
            pubDataToAllClients(data, socketChannel);
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
        if (!isServer) {
            return;
        }

        for (SocketChannel s : clientChannels.values()) {
            if (s != incomingChannel) {
                String peerAddr = s.socket().getInetAddress().getHostAddress();
                Log.d(TAG, "Server pub data to:  " + peerAddr);
                writeData(s, msg);
            }
        }
    }

    /**
     * the device want to push out data.
     * If the device is client, the only channel is to the server.
     * If the device is server, it just pub the data to all clients for now.
     */
    public int pushOutData(String jsonString) {
        if (!isServer) {   // device is client, can only send to server
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
        if (clientSocketChannel == null) {
            Log.d(TAG, "sendDataToServer: channel not connected ! waiting...");
            //Toast.makeText(this.mContext,"sendDataToServer: channel not connected ! waiting...",  Toast.LENGTH_SHORT).show();
            return 0;
        }
//        Log.d(TAG, "sendDataToServer: " + clientAddr + " -> " + clientSocketChannel.socket().getInetAddress().getHostAddress() + " : " + jsonString);
        //Toast.makeText(this.mContext,"sendDataToServer: " + clientAddr + " -> " + clientSocketChannel.socket().getInetAddress().getHostAddress() + " : " +  jsonString,  Toast.LENGTH_SHORT).show();
        return writeData(clientSocketChannel, jsonString);
    }
}
