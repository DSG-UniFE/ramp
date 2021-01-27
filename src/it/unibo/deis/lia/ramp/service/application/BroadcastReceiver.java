/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

//import java.net.*;

import java.util.Arrays;

//import java.util.*;

/**
 *
 * @author useruser
 */
public class BroadcastReceiver {
    private int clientPort;
    private String nodeId;
    //private Vector<InetAddress> address;
    private String[] address;

    /*public BroadcastReceiver(int clientPort, String[] address) {
        this.clientPort = clientPort;
        this.nodeId = null;
        this.address = address;
    }*/

    public BroadcastReceiver(int clientPort, String nodeId, String[] address) {
        this.clientPort = clientPort;
        this.nodeId = nodeId;
        this.address = address;
    }

    public String[] getAddress() {
        return address;
    }

    public String getNodeId() {
        return nodeId;
    }
    
    public int getClientPort() {
        return clientPort;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BroadcastReceiver other = (BroadcastReceiver) obj;
        if (this.clientPort != other.clientPort) {
            return false;
        }
        if (!Arrays.equals(this.address, other.address)) {
            return false;
        }
        if ((this.nodeId == null) ? (other.nodeId != null) : !this.nodeId.equals(other.nodeId)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + this.clientPort;
        hash = 67 * hash + (this.nodeId != null ? this.nodeId.hashCode() : 0);
        hash = 67 * hash + Arrays.deepHashCode(this.address);
        return hash;
    }

    @Override
    public String toString() {
        String stringAddress = "";
        for(String s : address){
            stringAddress += s+" ";
        }
        return nodeId+" "+stringAddress+""+clientPort;
    }

}
