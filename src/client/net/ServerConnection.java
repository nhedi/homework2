package client.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import common.MsgType;
import common.MessageSplitter;



/**
 * Manages all communication with the server. All operations are non-blocking.
 */
public class ServerConnection implements Runnable {
    private final ByteBuffer msgFromServer = ByteBuffer.allocateDirect(2018);
    private final Queue<ByteBuffer> messagesToSend = new ArrayDeque<>();
    private final MessageSplitter msgSplitter = new MessageSplitter();
    private final List<CommunicationListener> listeners = new ArrayList<>();
    private InetSocketAddress serverAddress;
    private SocketChannel socketChannel;
    private Selector selector;
    private boolean connected;
    private volatile boolean timeToSend = false;
    
    public void run() {
        try {
            initConnection();
            initSelector();

            while(connected || !messagesToSend.isEmpty()){
                if(timeToSend){
                    socketChannel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
                    timeToSend = false;
                }

                selector.select();
                for(SelectionKey key : selector.selectedKeys()){
                    selector.selectedKeys().remove(key);
                    if(!key.isValid()){
                        continue;
                    }
                    if(key.isConnectable()){
                        completeConnection(key);
                    } else if(key.isReadable()){
                        recvFromServer(key);
                    } else if(key.isWritable()){
                        sendToServer(key);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("LOST CONNECTION");
        } try {
            doDisconnect();
        } catch(IOException ex){
            System.err.println("COULD NOT DISCONNECT, WILL LEAVE UNGRACEFULLY!");
        }     
    }

    /**
     * Creates a new instance and connects to the specified server. Also starts a listener thread
     * receiving broadcast messages from server.
     *
     * @param host             Host name or IP address of server.
     * @param port             Server's port number.
     */
    public void connect(String host, int port){
       serverAddress = new InetSocketAddress(host, port);
       new Thread(this).start();
    }
    
    private void initSelector() throws IOException{
        selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
    }
    
    private void initConnection() throws IOException{
        socketChannel = socketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(serverAddress);
        connected = true;
    }
    
    private void completeConnection(SelectionKey key) throws IOException{
        socketChannel.finishConnect();
        key.interestOps(SelectionKey.OP_READ);
        try{
            InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            notifyConnectionDone(remoteAddress);
        }catch(IOException ioe){
            notifyConnectionDone(serverAddress);
        }
    }
    
    /**
     * Closes the connection with the server and stops the broadcast listener thread.
     *
     * @throws IOException If failed to close socket.
     */
    public void disconnect() throws IOException {
        connected = false;
        sendMsg(MsgType.DISCONNECT.toString(), null);
    }
    
    private void doDisconnect() throws IOException{
        socketChannel.close();
        socketChannel.keyFor(selector).cancel();
        notifyDisconnectionDone();
    }

    /**
     * Sends the user's username to the server. That username will be prepended to all messages
     * originating from this client, until a new username is specified.
     *
     * @param username The current user's username.
     */
    public void sendUsername(String username) {
        sendMsg(MsgType.USER.toString(), username);
    }


    public void sendGuess(String msg) {
        sendMsg(MsgType.GUESS.toString(), msg);
    }

    public void sendMsg(String... parts) {
        StringJoiner joiner = new StringJoiner("##");
        for (String part : parts) {
            joiner.add(part);
        }
        String messageWithLengthHeader = MessageSplitter.prependLengthHeader(joiner.toString());
        synchronized (messagesToSend) {
            messagesToSend.add(ByteBuffer.wrap(messageWithLengthHeader.getBytes()));
        }
        timeToSend = true;
        selector.wakeup();
    }
    
    private void sendToServer(SelectionKey key) throws IOException {
        ByteBuffer msg;
        synchronized (messagesToSend) {
            while ((msg = messagesToSend.peek()) != null) {
                socketChannel.write(msg);
                if (msg.hasRemaining()) {
                    return;
                }
                messagesToSend.remove();
            }
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void recvFromServer(SelectionKey key) throws IOException {
        msgFromServer.clear();
        int numOfReadBytes = socketChannel.read(msgFromServer);
        if (numOfReadBytes == -1) {
            throw new IOException("LOST CONNECTION");
        }
        String recvdString = extractMessageFromBuffer();
        msgSplitter.appendRecvdString(recvdString);
        while (msgSplitter.hasNext()) {
            String msg = msgSplitter.nextMsg();
            /* if (MessageSplitter.typeOf(msg) != MsgType.BROADCAST) {
                System.out.println("Received corrupt message: " + msg);
            }*/
            notifyMsgReceived(MessageSplitter.bodyOf(msg));
        }
    }

    private String extractMessageFromBuffer() {
        msgFromServer.flip();
        byte[] bytes = new byte[msgFromServer.remaining()];
        msgFromServer.get(bytes);
        System.out.println(new String(bytes));
        return new String(bytes);
    }
    
    private void notifyConnectionDone(InetSocketAddress connectedAddress) {
        Executor pool = ForkJoinPool.commonPool();
        for (CommunicationListener listener : listeners) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    listener.connected(connectedAddress);
                }
            });
        }
    }
    
    private void notifyDisconnectionDone() {
        Executor pool = ForkJoinPool.commonPool();
        for (CommunicationListener listener : listeners) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    listener.disconnected();
                }
            });
        }
    }
    
    private void notifyMsgReceived(String msg) {
        System.out.println("msg i nytifyer");
        System.out.println(msg);
        Executor pool = ForkJoinPool.commonPool();
        for (CommunicationListener listener : listeners) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    listener.recvdMsg(msg);
                }
            });
        }
    }
    
    public void addCommunicationListener(CommunicationListener listener){
        listeners.add(listener);
    }
}
