package client.net;
import java.net.InetSocketAddress;

/**
 * Recieves communication events
 */
public interface CommunicationListener {
    
    public void recvdMsg(String msg);
    
    public void connected(InetSocketAddress serverAddress);
    
    public void disconnected();
}
