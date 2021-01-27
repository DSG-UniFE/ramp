
package it.unibo.deis.lia.ramp.util;

import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import java.util.Arrays;


/**
 *
 * @author Carlo Giannelli
 */
public class EndPoint {
    private int port;
    private int nodeId;
    private String[] address;
    private int protocol = -1;

    public EndPoint(int port, int nodeId, String[] address) {
        this.port = port;
        this.nodeId = nodeId;
        this.address = address;
    }
    public EndPoint(int port, int nodeId, String[] address, int protocol) {
        this.port = port;
        this.nodeId = nodeId;
        this.address = address;
        this.protocol = protocol;
    }

    public String[] getAddress() {
        return address;
    }

    public int getNodeId() {
        return nodeId;
    }
    
    public int getPort() {
        return port;
    }
    
    public int getProtocol(){
    	return protocol;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EndPoint other = (EndPoint) obj;
        if (this.port != other.port) {
            return false;
        }
        if (!Arrays.equals(this.address, other.address)) {
            return false;
        }
        //if ((this.nodeId == null) ? (other.nodeId != null) : !this.nodeId.equals(other.nodeId)) {
        if ( this.nodeId != other.nodeId ) {
            return false;
        }
        if (this.protocol != other.protocol) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + this.port;
        hash = 67 * hash + this.nodeId;
        hash = 67 * hash + Arrays.deepHashCode(this.address);
        hash = 67 * hash + this.protocol;
        return hash;
    }

    @Override
    public String toString() {
        /*String stringAddress = "";
        for(String s : address){
            stringAddress += s+" ";
        }*/
    	if(protocol!=-1){
    		String stringProtocol;
    		switch(protocol){
    			case E2EComm.UDP: 	stringProtocol = "UDP"; 			break;
    			case E2EComm.TCP: 	stringProtocol = "TCP"; 			break;
    			default: 			stringProtocol = "unknownProtocol";	break;
    		}
    		return nodeId+" "+Arrays.toString(address)+":"+port+" via "+stringProtocol;
    	}
    	else{
    		return nodeId+" "+Arrays.toString(address)+":"+port;
    	}
    }

}
