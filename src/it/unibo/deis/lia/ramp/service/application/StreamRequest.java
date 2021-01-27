/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

/**
 *
 * @author useruser
 */
public class StreamRequest implements java.io.Serializable{
	
    /**
	 * 
	 */
	private static final long serialVersionUID = -5602170208541777791L;
	
	private String streamName;
    private int clientPort;
    private String streamProtocol;
    private String rampProtocol;

    public StreamRequest(String streamName, int clientPort) {
        this.streamName = streamName;
        this.clientPort = clientPort;
        this.streamProtocol = null;
    }
    public StreamRequest(String streamName, int clientPort, String streamProtocol, String rampProtocol) {
        this.streamName = streamName;
        this.clientPort = clientPort;
        this.streamProtocol = streamProtocol;
        this.rampProtocol = rampProtocol;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getStreamName() {
        return streamName;
    }

    public String getStreamProtocol() {
        return streamProtocol;
    }

    public String getRampProtocol() {
        return rampProtocol;
    }

    
}
