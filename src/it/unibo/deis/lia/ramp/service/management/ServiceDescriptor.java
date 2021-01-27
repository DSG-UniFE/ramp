
package it.unibo.deis.lia.ramp.service.management;

/**
 *
 * @author Carlo Giannelli
 */
public class ServiceDescriptor {
    
    private int serverPort;
    private int protocol;
    private String qos=null;

    public ServiceDescriptor(int serverPort, int protocol) {
        this.serverPort = serverPort;
        this.protocol = protocol;
    }

    public ServiceDescriptor(int serverPort, int protocol, String qos) {
        this.serverPort = serverPort;
        this.protocol = protocol;
        this.qos = qos;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getProtocol() {
        return protocol;
    }

    public String getQos() {
        return qos;
    }

    @Override
    public String toString() {
        return serverPort+" "+protocol;
    }

}
