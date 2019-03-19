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

/**
 * @author Alessandro Dolci
 */
public class FewestIntersectionsFlowPathSelector implements TopologyGraphSelector {

    private Graph topologyGraph;
    private Dijkstra dijkstra;

    public FewestIntersectionsFlowPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, null);
    }

    @Override
    public PathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, PathDescriptor> activePaths) {
        PathDescriptor bestPath = null;
        int bestNodeIntersections = Integer.MAX_VALUE;
        int bestAddressIntersections = Integer.MAX_VALUE;
        dijkstra.init(this.topologyGraph);
        dijkstra.setSource(this.topologyGraph.getNode(Integer.toString(sourceNodeId)));
        dijkstra.compute();
        Iterator<Path> availablePaths = dijkstra.getAllPathsIterator(this.topologyGraph.getNode(Integer.toString(destNodeId)));
        Map<Integer, Integer> multiPathEdgeCounts = new HashMap<Integer, Integer>();
        List<PathDescriptor> availablePathDescriptors = new ArrayList<PathDescriptor>();
        while (availablePaths.hasNext()) {
            Path path = availablePaths.next();
            List<String> addresses = new ArrayList<String>();
            List<Integer> nodeIds = new ArrayList<Integer>();
            for (int i = 0; i < path.getEdgeCount(); i++) {
                // Edge edge = path.getEdgePath().get(i);
                MultiNode currentNode = (MultiNode) path.getNodePath().get(i);
                MultiNode nextNode = (MultiNode) path.getNodePath().get(i+1);
                Collection<Edge> multiPathEdges = currentNode.getEdgeSetBetween(nextNode);
                Edge edge = null;
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
                System.out.println("LeastIntersectionsFlowPathSelector: next node address " + edge.getAttribute("address_" + nextNode.getId()));
                System.out.println("LeastIntersectionsFlowPathSelector: current node nodeId " + currentNode.getId());
            }
            nodeIds.add(Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            System.out.println("LeastIntersectionsFlowPathSelector: current node nodeId " + Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            PathDescriptor pathDescriptor = new PathDescriptor(addresses.toArray(new String[0]), nodeIds);
            availablePathDescriptors.add(pathDescriptor);
        }
        /*
         * Iterate over all the available paths between source and destination
         */
        for (PathDescriptor pathDescriptor : availablePathDescriptors) {
            int nodeIntersections = 0;
            /*
             * Iterate over the active flow paths to get the number of nodes
             * in common between any of them and the considered path
             */
            for (PathDescriptor activePathDescriptor : activePaths.values())
                for (int pathNodeId : pathDescriptor.getPathNodeIds())
                    for (int activePathNodeId : activePathDescriptor.getPathNodeIds())
                        if (pathNodeId == activePathNodeId)
                            nodeIntersections++;
            String[] path = pathDescriptor.getPath();
            int addressIntersections = 0;
            /*
             * Iterate over all the active flow paths to get the number of
             * addresses in common between any of them and the considered path
             */
            for (PathDescriptor activePathDescriptor : activePaths.values()) {
                String[] activePath = activePathDescriptor.getPath();
                for (String pathAddress : path)
                    for (String activePathAddress : activePath)
                        if (pathAddress.equals(activePathAddress))
                            addressIntersections++;
            }
            /*
             * If the path has a minor number of node intersections with the active paths, choose it as the current best path
             */
            if (nodeIntersections < bestNodeIntersections) {
                bestPath = pathDescriptor;
                bestNodeIntersections = nodeIntersections;
                bestAddressIntersections = addressIntersections;
            }
            /*
             * If the node has the same number of node intersections as the current best path, consider address intersections
             */
            else if (nodeIntersections == bestNodeIntersections) {
                /*
                 * Keep the path with the lowest amount of nodes in common with flow paths
                 */
                if (addressIntersections < bestAddressIntersections) {
                    bestPath = pathDescriptor;
                    bestAddressIntersections = addressIntersections;
                }
            }
        }
        return bestPath;
    }

    @Override
    public Map<Integer, PathDescriptor> getAllPathsFromSource(int sourceNodeId) {
        Map<Integer, PathDescriptor> paths = new HashMap<Integer, PathDescriptor>();
        dijkstra.init(this.topologyGraph);
        dijkstra.setSource(this.topologyGraph.getNode(Integer.toString(sourceNodeId)));
        dijkstra.compute();
        for (Node destNode : this.topologyGraph.getNodeSet()) {
            if (!destNode.equals(this.topologyGraph.getNode(Integer.toString(sourceNodeId)))) {
                Path path = dijkstra.getPath(destNode);
                List<String> addresses = new ArrayList<String>();
                List<Integer> nodeIds = new ArrayList<Integer>();
                for (int i = 0; i < path.getEdgeCount(); i++) {
                    Edge edge = path.getEdgePath().get(i);
                    Node currentNode = path.getNodePath().get(i);
                    Node nextNode = path.getNodePath().get(i+1);
                    addresses.add(edge.getAttribute("address_" + nextNode.getId()));
                    nodeIds.add(Integer.parseInt(currentNode.getId()));
                    System.out.println("LeastIntersectionsFlowPathSelector: next node address " + edge.getAttribute("address_" + nextNode.getId()));
                    System.out.println("LeastIntersectionsFlowPathSelector: current node nodeId " + currentNode.getId());
                }
                nodeIds.add(Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
                System.out.println("LeastIntersectionsFlowPathSelector: current node nodeId " + Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
                PathDescriptor pathDescriptor = new PathDescriptor(addresses.toArray(new String[0]), nodeIds);
                paths.put(Integer.parseInt(destNode.getId()), pathDescriptor);
            }
        }
        return paths;
    }
}
