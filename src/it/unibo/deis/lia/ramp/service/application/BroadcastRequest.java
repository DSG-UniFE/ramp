/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

/**
 *
 * @author useruser
 */
public class BroadcastRequest implements java.io.Serializable{
	
    /**
	 * 
	 */
	private static final long serialVersionUID = -8564653960968229575L;
	
	private String simpleProgramName;
    private int clientPort;
    private String message;

    public BroadcastRequest(String simpleProgramName, int clientPort, String message) {
        this.simpleProgramName = simpleProgramName;
        this.clientPort = clientPort;
        this.message = message;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getSimpleProgramName() {
        return simpleProgramName;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        if(message==null){
            return simpleProgramName+" "+clientPort;
        }
        else{
            return simpleProgramName+" "+clientPort+" "+message;
        }
    }
    
}
