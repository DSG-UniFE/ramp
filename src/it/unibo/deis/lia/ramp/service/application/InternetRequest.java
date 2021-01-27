
package it.unibo.deis.lia.ramp.service.application;


/**
 *
 * @author Carlo Giannelli
 */
public class InternetRequest implements java.io.Serializable{

    private static final long serialVersionUID = -4558248667103930799L;
	
	public static final int UDP = 0;
    public static final int TCP = 1;

    private String serverAddress;
    private int serverPort;
    private int clientPort;
    private int layer4Protocol;
    byte[] internetPayload;
    
    public InternetRequest(
            String serverAddress,
            int serverPort,
            int clientPort,
            int layer4Protocol,
            byte[] internetPayload
            ) {
        this.serverAddress=serverAddress;
        this.serverPort=serverPort;
        this.clientPort = clientPort;
        this.layer4Protocol = layer4Protocol;
        this.internetPayload = internetPayload;
    }

    public String getServerAddress() {
        return serverAddress;
    }
    public int getServerPort() {
        return serverPort;
    }
    public int getClientPort() {
        return clientPort;
    }
    public int getLayer4Protocol() {
        return layer4Protocol;
    }
    public byte[] getInternetPayload() {
        return internetPayload;
    }
    
}
