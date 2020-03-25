package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathSelectors;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.TopologyGraphSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import org.graphstream.graph.Graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * The idea behind this class is to make it available the possibility
 * for a ControllerClient to select the longest path in terms of hops count,
 *
 * TODO To be implemented
 */
public class LongestPathSelector implements TopologyGraphSelector {

    private Graph topologyGraph;

    public LongestPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
    }

    @Override
    public PathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, PathDescriptor> activePaths) {
        /*
         * Until a real implementation of this PathSelector is provided you can
         * define every PathDescriptor you want and return it to the ControllerService.
         */
        String[] path = new String[3];
        path[0] = "192.168.3.101";
        path[1] = "192.168.3.102";
        path[2] = "192.168.3.103";

        List<Integer> pathNodeIds = new ArrayList<>();
        pathNodeIds.add(2);
        pathNodeIds.add(3);
        pathNodeIds.add(4);

        return new PathDescriptor(path, pathNodeIds);
    }

    @Override
    public Map<Integer, PathDescriptor> getAllPathsFromSource(int sourceNodeId) {
        return null;
    }
}
