package server.net;

import common.MessageException;
import common.MessageSplitter;
import common.MsgType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.StringJoiner;
import server.controller.Controller;

public class HangmanServer {
    public static final int LINGER_TIME = 5000;
    public static final int TIME_HALF_HOUR = 1800000;
    private int portNo = 8080; // default
    private final Controller contr = new Controller();
    private final List<PlayerHandler> players = new ArrayList<>();
    private volatile boolean timeToBroadcast = false;
    private Selector selector;
    private ServerSocketChannel listeningSocketChannel;
    private final Queue<ByteBuffer> messagesToSend = new ArrayDeque<>();

    public static void main(String[] args) {
        HangmanServer server = new HangmanServer ();             
        server.parseArguments(args);
        server.serve();
    }
        
    void broadcast(String msg) {
        System.out.println("Inne i boradcast");
        System.out.println(msg);
        //contr.appendEntry(msg);
        timeToBroadcast = true;
        ByteBuffer completeMsg = createBroadcastMessage(msg);
        System.out.println("complete message i broadcast");
        System.out.println(completeMsg.toString());
        synchronized (messagesToSend) {
            messagesToSend.add(completeMsg);
        }
        System.out.println("messages to send array");
        System.out.println(messagesToSend.element().toString());
        System.out.println("syncronisat");
        selector.wakeup();
        System.out.println("Selector vaknad");
    }

    private ByteBuffer createBroadcastMessage(String msg) {
        StringJoiner joiner = new StringJoiner("##");
        joiner.add(MsgType.BROADCAST.toString());
        joiner.add(msg);
        String messageWithLengthHeader = MessageSplitter.prependLengthHeader(joiner.toString());
        System.out.println("medelande med headerlängd");
        System.out.println(messageWithLengthHeader);
        System.out.println(messageWithLengthHeader.getBytes().length);
        return ByteBuffer.wrap(messageWithLengthHeader.getBytes());
    }        //21561###BROADCAST##USER##Nat

    void removeHandler (PlayerHandler handler) {
        synchronized (players) {
            players.remove(handler);
        }
    }

    private void serve() {
        try {
            //selector.wakeup();
            initSelector();
            initListeningSocketChannel();
            startGame();
            System.out.println("initierat serve");
            while (true) {
                if (timeToBroadcast) {
                    writeOperationForAllActiveClients();
                    appendMsgToAllClientQueues();
                    timeToBroadcast = false;
                }
                selector.select();
                System.out.println("selected selector");
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        System.out.println("accepterat connect nyckeln");
                        startHandler(key);
                        System.out.println("antal servertrådar igång");
                        System.out.println(java.lang.Thread.activeCount());
                    } else if (key.isReadable()) {
                        recvFromClient(key);
                    } else if (key.isWritable()) {
                        sendToClient(key);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Server failure.");
        }
    }

    public void startGame(){
        contr.selectedWord();
        System.out.println("Lyckades välja ord");
        broadcast(MsgType.NEWGAME + "##" + contr.showCurrentState() + "##" + contr.remainingGuesses());
        System.out.println("Lyckades broadcasta NEW GAME");
    }

//    private void startHandler(Socket playerSocket) throws SocketException{
//            playerSocket.setSoLinger(true, LINGER_TIME);
//            playerSocket.setSoTimeout(TIME_HALF_HOUR);
//            PlayerHandler handler = new PlayerHandler(this, playerSocket, contr.getGameStatus(), contr);
//            synchronized (players) {
//                players.add(handler);
//            } 
//            Thread handlerThread = new Thread(handler);
//            handlerThread.setPriority(Thread.MAX_PRIORITY);
//            handlerThread.start();		
//    }
    
    private void startHandler(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        PlayerHandler handler = new PlayerHandler(this, clientChannel, contr);
        clientChannel.register(selector, SelectionKey.OP_WRITE, new Client(handler, contr.
                                                                           getGameStatus()));
        clientChannel.setOption(StandardSocketOptions.SO_LINGER, LINGER_TIME); //Close will probably
        //block on some JVMs.
        // clientChannel.socket().setSoTimeout(TIMEOUT_HALF_HOUR); Timeout is not supported on 
        // socket channels. Could be implemented using a separate timer that is checked whenever the
        // select() method in the main loop returns.
    }

    private void parseArguments(String[] arguments) {
        if (arguments.length > 0) {
            try {
                    portNo = Integer.parseInt(arguments[1]);
            } catch(NumberFormatException e) {
                    System.err.println("Invalid port number, using default");
            }
        }
    } 
    
    private void initSelector() throws IOException {
        selector = Selector.open();
    }

    private void initListeningSocketChannel() throws IOException {
        listeningSocketChannel = ServerSocketChannel.open();
        listeningSocketChannel.configureBlocking(false);
        listeningSocketChannel.bind(new InetSocketAddress(portNo));
        listeningSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }
    
        private void writeOperationForAllActiveClients() {
        for (SelectionKey key : selector.keys()) {
            if (key.channel() instanceof SocketChannel && key.isValid()) {
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private void appendMsgToAllClientQueues() {
        synchronized (messagesToSend) {
            ByteBuffer msgToSend;
            while ((msgToSend = messagesToSend.poll()) != null) {
                for (SelectionKey key : selector.keys()) {
                    Client client = (Client) key.attachment();
                    if (client == null) {
                        continue;
                    }
                    synchronized (client.messagesToSend) {
                        client.queueMsgToSend(msgToSend);

                    }
                }
            }
        }
    }
    
    private void recvFromClient(SelectionKey key) throws IOException {
        Client client = (Client) key.attachment();
        try {
            client.handler.recvMsg();
        } catch (IOException clientHasClosedConnection) {
            removeClient(key);
        }
    }

    private void sendToClient(SelectionKey key) throws IOException {
        Client client = (Client) key.attachment();
        try {
            client.sendAll();
            key.interestOps(SelectionKey.OP_READ);
        } catch (MessageException couldNotSendAllMessages) {
        } catch (IOException clientHasClosedConnection) {
            removeClient(key);
        }
    }
    
    private void removeClient(SelectionKey clientKey) throws IOException {
        Client client = (Client) clientKey.attachment();
        client.handler.disconnectClient();
        clientKey.cancel();
    }
    
    private class Client {
        private final PlayerHandler handler;
        private final Queue<ByteBuffer> messagesToSend = new ArrayDeque<>();

        private Client(PlayerHandler handler, String[] conversation) {
            this.handler = handler;
            for (String entry : conversation) {
                messagesToSend.add(createBroadcastMessage(entry));
            }
        }

        private void queueMsgToSend(ByteBuffer msg) {
            synchronized (messagesToSend) {
                messagesToSend.add(msg.duplicate());
            }
        }

        private void sendAll() throws IOException, MessageException {
            ByteBuffer msg = null;
            synchronized (messagesToSend) {
                while ((msg = messagesToSend.peek()) != null) {
                    handler.sendMsg(msg);
                    messagesToSend.remove();
                }
            }
        }
    }
}