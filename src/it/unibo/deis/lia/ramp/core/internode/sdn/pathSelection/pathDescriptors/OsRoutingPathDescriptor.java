package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors;

import java.io.Serializable;
import java.util.List;

public class OsRoutingPathDescriptor extends PathDescriptor implements Serializable {

    private static final long serialVersionUID = -7007761956766072449L;

    private String sourceIp;
    private String destinationIP;
    private int sourceNodeId;

    public OsRoutingPathDescriptor(String[] path, List<Integer> pathNodeIds, String sourceIp) {
        super(path, pathNodeIds);
        this.sourceNodeId = pathNodeIds.get(0);
        this.sourceIp = sourceIp;
        this.destinationIP = path[path.length-1];
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getSourceIp() {
        return this.sourceIp;
    }

    public String getDestinationIP() {
        return this.destinationIP;
    }

    public void setSourceNodeId(int sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public int getSourceNodeId() {
        return this.sourceNodeId;
    }
}
