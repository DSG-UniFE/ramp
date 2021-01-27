
package it.unibo.deis.lia.ramp.service.management;

/**
 *
 * @author Carlo Giannelli
 */
public class ServiceRequest implements java.io.Serializable{
	
    private static final long serialVersionUID = -7779239692256354505L;
	
	private String serviceName;
    private int clientPort;
    private String qos = null; // (optional)

    public ServiceRequest(String serviceName, int clientPort) {
        this.serviceName = serviceName;
        this.clientPort = clientPort;
    }

    public ServiceRequest(String serviceName, int clientPort, String qos) {
        this.serviceName = serviceName;
        this.clientPort = clientPort;
        this.qos = qos;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getQos() {
        return qos;
    }

    public String getServiceName() {
        return serviceName;
    }

    
}
