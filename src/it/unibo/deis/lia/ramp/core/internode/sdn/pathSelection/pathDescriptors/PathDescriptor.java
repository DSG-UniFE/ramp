package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors;

import java.io.Serializable;
import java.util.List;

/**
 * @author Alessandro Dolci
 */
public class PathDescriptor implements Serializable {
    private static final long serialVersionUID = 996550170142113520L;
    protected String[] path;
    protected List<Integer> pathNodeIds;
    protected long creationTime; // milliseconds since the Epoch

    public PathDescriptor(String[] path) {
        this.path = path;
        this.creationTime = System.currentTimeMillis();
    }

    public PathDescriptor(String[] path, List<Integer> pathNodeIds) {
        this.path = path;
        this.pathNodeIds = pathNodeIds;
    }

    public String[] getPath() {
        return path;
    }

    public void setPath(String[] path) {
        this.path = path;
    }

    public List<Integer> getPathNodeIds() {
        return pathNodeIds;
    }

    public void setPathNodeIds(List<Integer> pathNodeIds) {
        this.pathNodeIds = pathNodeIds;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public int getDestinationNodeId() { return pathNodeIds.get(pathNodeIds.size() - 1); }
}
