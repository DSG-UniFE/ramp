package it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager.ipRouteRule;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This class stores the info associated to
 * the "ip route add" command executed by the
 * OsRoutingManager. This object will be used
 * also to execute the command "ip route del".
 */
public class IpRouteRule {

    private String sourceIP;
    private String viaIP;
    private String destinationIP;

    /**
     * This constructor is used by the sender of a route.
     *
     * @param sourceIP sourceIP of the sender
     * @param viaIP nextHop
     * @param destinationIP route destination
     */
    public IpRouteRule(String sourceIP, String viaIP, String destinationIP) {
        this.sourceIP = sourceIP;
        this.viaIP = viaIP;
        this.destinationIP = destinationIP;
    }

    /**
     * This constructor is used by an intermediate
     * node of a route.
     *
     * @param viaIP nextHop
     * @param destinationIP route destination
     */
    public IpRouteRule(String viaIP, String destinationIP) {
        this.sourceIP = null;
        this.viaIP = viaIP;
        this.destinationIP = destinationIP;
    }

    public String getSourceIP() {
        return sourceIP;
    }

    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }

    public String getViaIP() {
        return viaIP;
    }

    public void setViaIP(String viaIP) {
        this.viaIP = viaIP;
    }

    public String getDestinationIP() {
        return destinationIP;
    }

    public void setDestinationIP(String destinationIP) {
        this.destinationIP = destinationIP;
    }
}
