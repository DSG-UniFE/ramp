
package it.unibo.deis.lia.ramp.service.management;


/**
 *
 * @author Carlo Giannelli
 */
public class ServiceResponse implements java.io.Serializable{
	
    /**
	 * 
	 */
	private static final long serialVersionUID = 5994054093761340049L;
	
	private String serviceName;
	transient private String[] serverDest;
    private int serverPort;
    private int protocol;
    transient private int serverNodeId;
    private String qos = null; // (optional)

    public ServiceResponse(String serviceName, ServiceDescriptor sd) {
        this.serviceName = serviceName;
        this.serverPort = sd.getServerPort();
        this.protocol = sd.getProtocol();
        this.qos  = sd.getQos();
    }

    public ServiceResponse(String serviceName, int serverPort, int protocol) {
        this.serviceName = serviceName;
        this.serverPort = serverPort;
        this.protocol = protocol;
    }

    public ServiceResponse(String serviceName, int serverPort, int protocol, String qos) {
        this.serviceName = serviceName;
        this.serverPort = serverPort;
        this.protocol = protocol;
        this.qos = qos;
    }
    
    public int getProtocol() {
        return protocol;
    }

    public String getQos() {
        return qos;
    }

    public int getServerPort() {
        return serverPort;
    }
    
    public int getServerNodeId() {
		return serverNodeId;
	}
	public void setServerNodeId(int serverNodeId) {
		this.serverNodeId = serverNodeId;
	}

	public String[] getServerDest() {
        return serverDest;
    }
    public void setServerDest(String[] serverDest) {
        this.serverDest = serverDest;
    }

    @Override
    public String toString() {
        String res = serviceName + " @ ";
        res += "[";
        if(serverDest!=null){
            for(int i=0; i<serverDest.length-1; i++){
                res += serverDest[i]+", ";
            }
            res += serverDest[serverDest.length-1];
        }
        else{
            res += "null";
        }
        res += "]:"+serverPort;
        res += " nodeId:"+serverNodeId;
        return res;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final ServiceResponse other = (ServiceResponse) obj;

        if(this.serverDest==null || other.getServerDest()==null){
            return false;
        }

        if( this.serverDest.length != other.getServerDest().length ){
            return false;
        }

        for(int i=0; i< this.serverDest.length; i++){
            if( ! this.serverDest[i].equals(other.getServerDest()[i])){
                return false;
            }
        }
        
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.serverDest != null ? this.serverDest.hashCode() : 0);
        return hash;
    }

    public String getServiceName() {
        return serviceName;
    }

}
