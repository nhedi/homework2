package server.net;

import common.MessageException;
import common.MessageSplitter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.StringJoiner;

import common.MsgType;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ForkJoinPool;
import server.controller.Controller;


/**
 *
 * @author yuchen
 */
public class PlayerHandler {
    private final HangmanServer server;
    private final SocketChannel playerChannel;
    //private final String[] gameWhenStarting;
    private final Controller contr;
    private PrintWriter toPlayer;
    
    private static final String JOIN_MSG = " joined the game, welcome!";
    private String username = "anonymous";
    private String guess;
    private static final String LEAVE_MSG = " left the game :( .";
    private static final String USER_DELIMETER = ": ";
    private static final String WORD_MSG = "The word is ";
    private final MessageSplitter msgSplitter = new MessageSplitter();
    private final ByteBuffer msgFromClient = ByteBuffer.allocateDirect(2018);
    
    PlayerHandler(HangmanServer server, SocketChannel playerChannel, Controller contr) {
        this.server = server;
        this.playerChannel = playerChannel;
        this.contr = contr;
    }
    
    //@Override
    public void run1() {
        //for (String entry : gameWhenStarting) {
            //sendHistory(entry);
        //}
        while (msgSplitter.hasNext()) {
            Message msg = new Message(msgSplitter.nextMsg());
            switch(msg.msgType) {
                case USER:
                    System.out.println("username från client");
                    System.out.println(msg.msgBody);
                    username = msg.msgBody;
                    server.broadcast(msg.msgType + "##" + username);
                    break;

                case GUESS:
                    guess = msg.msgBody;
                    contr.playGame(guess);
                    boolean right = contr.correctWord();
                    int remainingGuessses = contr.remainingGuesses();

                    StringJoiner joiner = new StringJoiner("##");
                    joiner.add(username);
                    joiner.add(guess);
                    joiner.add(contr.showCurrentState());
                    joiner.add(Integer.toString(remainingGuessses));

                    server.broadcast(msg.msgType + "##" + joiner.toString());
                    if(remainingGuessses == 0) {
                        joiner.add(Integer.toString(contr.score()));
                        joiner.add("lose");
                        joiner.add(contr.getWord());
                        server.broadcast(MsgType.ENDGAME + "##" + joiner.toString());
                        new Thread (()->{ server.startGame(); }).start();
                    } else if(right) {
                        joiner.add(Integer.toString(contr.score()));
                        joiner.add("win");
                        server.broadcast(MsgType.ENDGAME + "##" + joiner.toString());
                        new Thread (()->{ server.startGame(); }).start();
                    }
                    System.out.println(guess);
                    break;

                case DISCONNECT:
                    System.out.println(msg);
                    try {
                    disconnectClient();
                    } catch(IOException ioe) {};
                    server.broadcast(msg.msgType + "##" + username);
                    break; 

                default:
                    System.out.println("Command:" + msg.receivedString + "is not known.");
            }
        }
    }
    
    // package-private method
    void sendHistory (String msg) {
        StringJoiner joiner = new StringJoiner("##");
        joiner.add(MsgType.BROADCAST.toString());
        joiner.add(msg);
        toPlayer.println(joiner.toString());
    }

    void sendMsg(ByteBuffer msg) throws IOException {
        playerChannel.write(msg);
        if (msg.hasRemaining()) {
            throw new MessageException("Could not send message");
        }
    }
    
    void disconnectClient() throws IOException {
        playerChannel.close();
    }
    
    void recvMsg() throws IOException {
        msgFromClient.clear();
        int numOfReadBytes;
        numOfReadBytes = playerChannel.read(msgFromClient);
        if (numOfReadBytes == -1) {
            throw new IOException("Client has closed connection.");
        }
        String recvdString = extractMessageFromBuffer();
        msgSplitter.appendRecvdString(recvdString);
        run1();
        //ForkJoinPool.commonPool().execute(this);
    }
    
    private String extractMessageFromBuffer() {
        msgFromClient.flip();
        byte[] bytes = new byte[msgFromClient.remaining()];
        msgFromClient.get(bytes);
        return new String(bytes);
    }    
    
    private static class Message {
        private String receivedString;
        private MsgType msgType;
        private String msgBody;
        
        private Message (String receivedString) {
            parse(receivedString);
            this.receivedString = receivedString;
        }
        
        private void parse (String strToParse) {
            try {
                String[] msgTokens = strToParse.split("##");
                msgType = MsgType.valueOf(msgTokens[0].toUpperCase());
                if (hasBody(msgTokens)) {
                    msgBody = msgTokens[1];
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        
        private boolean hasBody(String[] msgTokens) {
            return msgTokens.length > 1;
        }
    }
}
