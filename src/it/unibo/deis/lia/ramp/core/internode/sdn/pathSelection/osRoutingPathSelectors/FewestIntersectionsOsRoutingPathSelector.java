package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;
import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import org.graphstream.graph.implementations.MultiNode;

import java.util.*;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This is class is a modified version of FewestIntersectionsFlowPathSelector.
 * TODO The algorithm does not work properly in case of multiple interfaces, it is not able
 *  to retrieve all the path according to the result of the function dijkstra.getAllPathsIterator
 */
public class FewestIntersectionsOsRoutingPathSelector implements OsRoutingTopologyGraphSelector {

    private Graph topologyGraph;
    private Dijkstra dijkstra;

    public FewestIntersectionsOsRoutingPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, null);
    }

    @Override
    public OsRoutingPathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, OsRoutingPathDescriptor> activePaths) {
        OsRoutingPathDescriptor bestPath = null;
        int bestNodeIntersections = Integer.MAX_VALUE;
        int bestAddressIntersections = Integer.MAX_VALUE;
        dijkstra.init(this.topologyGraph);
        dijkstra.setSource(this.topologyGraph.getNode(Integer.toString(sourceNodeId)));
        dijkstra.compute();
        Iterator<Path> availablePaths = dijkstra.getAllPathsIterator(this.topologyGraph.getNode(Integer.toString(destNodeId)));

        Map<Integer, Integer> multiPathEdgeCounts = new HashMap<Integer, Integer>();
        List<OsRoutingPathDescriptor> availablePathDescriptors = new ArrayList<>();
        while (availablePaths.hasNext()) {
            Path path = availablePaths.next();
            List<String> addresses = new ArrayList<String>();
            List<Integer> nodeIds = new ArrayList<Integer>();
            String sourceIp = null;
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
                if(i == 0) {
                    sourceIp = edge.getAttribute("address_" + currentNode.getId());
                }
                nodeIds.add(Integer.parseInt(currentNode.getId()));
                System.out.println("FewestIntersectionsOsRoutingPathSelector: next node address " + edge.getAttribute("address_" + nextNode.getId()));
                System.out.println("FewestIntersectionsOsRoutingPathSelector: current node nodeId " + currentNode.getId());
            }
            nodeIds.add(Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            System.out.println("FewestIntersectionsOsRoutingPathSelector: current node nodeId " + Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            OsRoutingPathDescriptor pathDescriptor = new OsRoutingPathDescriptor(addresses.toArray(new String[0]), nodeIds, sourceIp);
            availablePathDescriptors.add(pathDescriptor);
        }
        /*
         * Iterate over all the available paths between source and destination
         */
        for (OsRoutingPathDescriptor pathDescriptor : availablePathDescriptors) {
            int nodeIntersections = 0;
            /*
             * Iterate over the active flow paths to get the number of nodes
             * in common between any of them and the considered path
             */
            for (OsRoutingPathDescriptor activePathDescriptor : activePaths.values())
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
            for (OsRoutingPathDescriptor activePathDescriptor : activePaths.values()) {
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
    public Map<Integer, OsRoutingPathDescriptor> getAllPathsFromSource(int sourceNodeId) {
        Map<Integer, OsRoutingPathDescriptor> paths = new HashMap<>();
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
                    System.out.println("FewestIntersectionsOsRoutingPathSelector: next node address " + edge.getAttribute("address_" + nextNode.getId()));
                    System.out.println("FewestIntersectionsOsRoutingPathSelector: current node nodeId " + currentNode.getId());
                }
                nodeIds.add(Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
                System.out.println("FewestIntersectionsOsRoutingPathSelector: current node nodeId " + Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
                //OsRoutingPathDescriptor pathDescriptor = new PathDescriptor(addresses.toArray(new String[0]), nodeIds);
                //paths.put(Integer.parseInt(destNode.getId()), pathDescriptor);
            }
        }
        return paths;
    }

    @Override
    public OsRoutingPathDescriptor reversePath(OsRoutingPathDescriptor path) {
        String sourceIp = path.getDestinationIP();

        List<Integer> pathNodeIds = new ArrayList<>(path.getPathNodeIds());
        Collections.reverse(pathNodeIds);

        List<String> reversedForwardPath = new ArrayList<String>(Arrays.asList(path.getPath()));
        int edgesNumber = reversedForwardPath.size();
        Collections.reverse(reversedForwardPath);
        List<String> newPath = new ArrayList<>();

        for (int i = 0; i < edgesNumber; i++) {
            MultiNode currentNode = this.topologyGraph.getNode(Integer.toString(pathNodeIds.get(i)));
            MultiNode nextNode = this.topologyGraph.getNode(Integer.toString(pathNodeIds.get(i+1)));
            /*
             * We insert all this checks because the topology can change and
             * this node may be not present anymore due to their mobility.
             */
            if(currentNode == null || nextNode == null) {
                return null;
            }
            Collection<Edge> edgeSet = currentNode.getEdgeSetBetween(nextNode);
            for(Edge edge : edgeSet) {
                String referenceAddress = edge.getAttribute("address_" + currentNode.getId());

                if(referenceAddress.equals(reversedForwardPath.get(i))) {
                    String reverseAddress = edge.getAttribute("address_" + nextNode.getId());
                    if(reverseAddress == null) {
                        return null;
                    }
                    newPath.add(reverseAddress);
                    break;
                }
            }
        }

        return new OsRoutingPathDescriptor(newPath.toArray(new String[0]), pathNodeIds, sourceIp);
    }
}
