package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathSelectors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.TopologyGraphSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;

import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import org.graphstream.graph.implementations.MultiNode;

import it.unibo.deis.lia.ramp.util.NetworkInterfaceStats;

/**
 * @author Alessandro Dolci
 */
public class MinimumNetworkLoadFlowPathSelector implements TopologyGraphSelector {

    private Graph topologyGraph;
    private Dijkstra dijkstra;

    public MinimumNetworkLoadFlowPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, null);
    }

    @Override
    public PathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements,
                                     Map<Integer, PathDescriptor> activePaths) {
        PathDescriptor bestPath = null;
        long bestBytesReceived = Long.MAX_VALUE;
        dijkstra.init(this.topologyGraph);
        dijkstra.setSource(this.topologyGraph.getNode(Integer.toString(sourceNodeId)));
        dijkstra.compute();
        Iterator<Path> availablePaths = dijkstra.getAllPathsIterator(this.topologyGraph.getNode(Integer.toString(destNodeId)));
        Map<Integer, Integer> multiPathEdgeCounts = new HashMap<>();
        List<PathDescriptor> availablePathDescriptors = new ArrayList<>();
        while (availablePaths.hasNext()) {
            Path path = availablePaths.next();
            List<String> addresses = new ArrayList<>();
            List<Integer> nodeIds = new ArrayList<>();
            for (int i = 0; i < path.getEdgeCount(); i++) {
                // Edge edge = path.getEdgePath().get(i);
                MultiNode currentNode = (MultiNode) path.getNodePath().get(i);
                MultiNode nextNode = (MultiNode) path.getNodePath().get(i+1);
                Collection<Edge> multiPathEdges = currentNode.getEdgeSetBetween(nextNode);
                Edge edge;
                if (multiPathEdges.size() == 1)
                    edge = multiPathEdges.iterator().next();
                else {
                    Integer multiPathEdgeCount = multiPathEdgeCounts.get(i);
                    if (multiPathEdgeCount != null)
                        multiPathEdgeCount++;
                    else
                        multiPathEdgeCount = 0;
                    edge = multiPathEdges.toArray(new Edge[0])[multiPathEdgeCount];
                    multiPathEdgeCounts.put(i, multiPathEdgeCount);
                }
                addresses.add(edge.getAttribute("address_" + nextNode.getId()));
                nodeIds.add(Integer.parseInt(currentNode.getId()));
                System.out.println("MinimumNetworkLoadFlowPathSelector: next node address " + edge.getAttribute("address_" + nextNode.getId()));
                System.out.println("MinimumNetworkLoadFlowPathSelector: current node nodeId " + currentNode.getId());
            }
            nodeIds.add(Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            System.out.println("MinimumNetworkLoadFlowPathSelector: current node nodeId " + Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            PathDescriptor pathDescriptor = new PathDescriptor(addresses.toArray(new String[0]), nodeIds);
            availablePathDescriptors.add(pathDescriptor);
        }
        /*
         * Iterate over the available paths to find the less loaded one
         */
        for (PathDescriptor pathDescriptor : availablePathDescriptors) {
            long bytesReceived = 0;
            for (int i = 0; i < pathDescriptor.getPath().length; i++) {
                String address = pathDescriptor.getPath()[i];
                MultiNode node = this.topologyGraph.getNode(Integer.toString(pathDescriptor.getPathNodeIds().get(i+1)));
                NetworkInterfaceStats nodeNetworkInterfaceStats = node.getAttribute("network_stats_" + address);
                bytesReceived = bytesReceived + nodeNetworkInterfaceStats.getReceivedBytes();
            }
            for (String address : pathDescriptor.getPath())
                System.out.println("MinimumNetworkLoadFlowPathSelector: path " + address);
            System.out.println("MinimumNetworkLoadFlowPathSelector: bytes received " + bytesReceived);
            if (bytesReceived < bestBytesReceived) {
                bestPath = pathDescriptor;
                bestBytesReceived = bytesReceived;
            }
        }
        return bestPath;
    }

    @Override
    public Map<Integer, PathDescriptor> getAllPathsFromSource(int sourceNodeId) {
        Map<Integer, PathDescriptor> paths = new HashMap<>();
        dijkstra.init(this.topologyGraph);
        dijkstra.setSource(this.topologyGraph.getNode(Integer.toString(sourceNodeId)));
        dijkstra.compute();
        for (Node destNode : this.topologyGraph.getNodeSet()) {
            if (!destNode.equals(this.topologyGraph.getNode(Integer.toString(sourceNodeId)))) {
                PathDescriptor pathDescriptor = selectPath(sourceNodeId, Integer.parseInt(destNode.getId()), null, null);
                paths.put(Integer.parseInt(destNode.getId()), pathDescriptor);
            }
        }
        return paths;
    }
}
