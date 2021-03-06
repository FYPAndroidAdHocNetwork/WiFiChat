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
 * this class encapsulates the NIO buffer and NIO channel on top of socket. It is all abt NIO style.
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
    private SelectionKey selectionKey = null;
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
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));  // bind to the port to listen.
        return serverSocketChannel;
    }

    /**
     * Creates a non-blocking socket channel to connect to specified host name and port.
     * connect() is called on the new channel before it is returned.
     */
    public static SocketChannel createSocketChannel(String hostName, int port) throws IOException {
        // Create a non-blocking socket channel
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        // Send a connection request to the server; this method is non-blocking
        socketChannel.connect(new InetSocketAddress(hostName, port));
        return socketChannel;
    }

    /**
     * create a socket channel and connect to the host.
     * after return, the socket channel guarantee to be connected.
     */
    public SocketChannel connectTo(String hostname, int port) {
        SocketChannel socketChannel;
        try {
            socketChannel = createSocketChannel(hostname, port);  // connect to the remote host, port
            // Before the socket is usable, the connection must be completed. finishConnect().
            while (!socketChannel.finishConnect()) {
                // blocking spin lock
            }
            // Socket channel is now ready to use
        } catch (IOException e) {
            Log.e(TAG, "connectTo: exception : " + e.toString());
            return null;
        }
        return socketChannel;
    }

    /**
     * client, after p2p connection available, connect to group owner and select monitoring the sockets.
     * start blocking selector monitoring in an async task, infinite loop
     */
    public int startClientSelector(String host) {
        closeClient();
        closeServer();   // close linger server.

        if (clientSocketChannel != null) {
            Log.d(TAG, "startClientSelector: already connected; client addr: " + clientSocketChannel.socket().getLocalAddress().getHostAddress());
            return 0;
        }

        try {
            // connected to the server upon start client.
            clientSocketChannel = connectTo(host, PORT_NUMBER);
            if (clientSocketChannel == null) {
                Log.e(TAG, "failed to connect to server");
                return -1;
            }

            selector = Selector.open();
            clientAddr = clientSocketChannel.socket().getLocalAddress().getHostName();
            selectionKey = clientSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            ((WiFiChatApp) connectionService.getApplication()).setMyAddr(clientAddr);
            Log.d(TAG, "Client Selector started; client addr: " + clientSocketChannel.socket().getLocalAddress().getHostAddress());
        } catch (Exception e) {
            Log.e(TAG, "startClientSelector exception: " + e.toString());
            selector = null;
            clientSocketChannel = null;
            return -1;
        }

        // start selector monitoring, blocking
        new SelectorAsyncTask(connectionService, selector).execute();
        Log.d(TAG, "client selector started");
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
            serverSocketChannel = createServerSocketChannel(PORT_NUMBER); // BindException if already bind.

            if (serverSocketChannel == null) {
                Log.e(TAG, "failed to createServerSocketChannel");
                return -1;
            }

            serverAddr = this.serverSocketChannel.socket().getInetAddress().getHostAddress();
            if ("0.0.0.0".equals(serverAddr)) {
                serverAddr = "Header";
            }
            Log.d(TAG, "server addr: " + serverAddr);
            ((WiFiChatApp) connectionService.getApplication()).setMyAddr(serverAddr);

            selector = Selector.open();
            selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            selectionKey.attach("accept_channel");
            isServer = true;
        } catch (Exception e) {
            Log.e(TAG, "startServerSelector exception: " + e.toString());
            return -1;
        }

        new SelectorAsyncTask(connectionService, selector).execute();
        Log.d(TAG, "server selector started");
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
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
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
        Log.d(TAG, "server closed");
    }

    private void closeClient() {
        if (clientSocketChannel != null) {
            try {
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
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
        Log.d(TAG, "client closed");
    }

    /**
     * read out -1, connection broken, remove it from clients collection
     */
    public void onBrokenConn(SocketChannel socketChannel) {
        //todo why this method keeps being called when connection breaks?
        if (socketChannel.socket().getInetAddress() != null) {
            String peerAddr = socketChannel.socket().getInetAddress().getHostAddress();
            if (isServer) {
                clientChannels.remove(peerAddr);
            } else {
                closeClient();
            }
        }
    }

    /**
     * Server handle new client coming in.
     */
    public void onNewClient(SocketChannel socketChannel) {
        String clientAddr = socketChannel.socket().getInetAddress().getHostAddress();
        Log.d(TAG, "onNewClient: " + clientAddr);
        clientChannels.put(clientAddr, socketChannel);
    }

    /**
     * Client's connect to server success,
     */
    public void onFinishConnect(SocketChannel socketChannel) {
        String clientAddr = socketChannel.socket().getLocalAddress().getHostAddress();
        String serverAddr = socketChannel.socket().getInetAddress().getHostAddress();
        Log.d(TAG, "onFinishConnect: " + clientAddr + " -> " + serverAddr);
        clientSocketChannel = socketChannel;
        this.clientAddr = clientAddr;
        ((WiFiChatApp) connectionService.getApplication()).setMyAddr(this.clientAddr);
    }

    /**
     * write byte buf to the socket channel.
     */
    private int writeData(SocketChannel socketChannel, String jsonString) {
        byte[] buf = jsonString.getBytes();
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);  // wrap the buf into byte buffer
        int numOfBytesWritten = 0;
        try {
            //byteBuffer.flip();  // no flip after creating from wrap.
            numOfBytesWritten = socketChannel.write(byteBuffer);
        } catch (Exception e) {
            // Connection may have been closed
            Log.e(TAG, "writeData exception : " + e.toString());
            onBrokenConn(socketChannel);
        }
        return numOfBytesWritten;
    }

    /**
     * server publish data to all the connected clients
     */
    public void pubDataToAllClients(String msg, SocketChannel incomingChannel) {
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
    private void sendDataToServer(String jsonString) {
        if (clientSocketChannel == null) {
            Log.e(TAG, "sendDataToServer: channel not connected ! waiting...");
            return;
        }
        writeData(clientSocketChannel, jsonString);
    }
}
