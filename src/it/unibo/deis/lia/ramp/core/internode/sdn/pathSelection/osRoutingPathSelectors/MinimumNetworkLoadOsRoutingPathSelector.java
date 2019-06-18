package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.util.NetworkInterfaceStats;
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
 * This is class is a modified version of MinimumNetworkLoadFlowPathSelector.
 * TODO The algorithm does not work properly in case of multiple interfaces, it is not able
 *  to retrieve all the path according to the result of the function dijkstra.getAllPathsIterator
 */
public class MinimumNetworkLoadOsRoutingPathSelector implements OsRoutingTopologyGraphSelector {

    private Graph topologyGraph;
    private Dijkstra dijkstra;

    public MinimumNetworkLoadOsRoutingPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, null);
    }

    @Override
    public OsRoutingPathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, OsRoutingPathDescriptor> activePaths) {
        OsRoutingPathDescriptor bestPath = null;
        long bestBytesReceived = Long.MAX_VALUE;
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
                System.out.println("MinimumNetworkLoadOsRoutingPathSelector: next node address " + edge.getAttribute("address_" + nextNode.getId()));
                System.out.println("MinimumNetworkLoadOsRoutingPathSelector: current node nodeId " + currentNode.getId());
            }
            nodeIds.add(Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            System.out.println("MinimumNetworkLoadOsRoutingPathSelector: current node nodeId " + Integer.parseInt(path.getNodePath().get(path.getNodePath().size()-1).getId()));
            OsRoutingPathDescriptor pathDescriptor = new OsRoutingPathDescriptor(addresses.toArray(new String[0]), nodeIds, sourceIp);
            availablePathDescriptors.add(pathDescriptor);
        }
        /*
         * Iterate over the available paths to find the less loaded one
         */
        for (OsRoutingPathDescriptor pathDescriptor : availablePathDescriptors) {
            long bytesReceived = 0;
            for (int i = 0; i < pathDescriptor.getPath().length; i++) {
                String address = pathDescriptor.getPath()[i];
                MultiNode node = this.topologyGraph.getNode(Integer.toString(pathDescriptor.getPathNodeIds().get(i+1)));
                NetworkInterfaceStats nodeNetworkInterfaceStats = node.getAttribute("network_stats_" + address);
                bytesReceived = bytesReceived + nodeNetworkInterfaceStats.getReceivedBytes();
            }
            for (String address : pathDescriptor.getPath())
                System.out.println("MinimumNetworkLoadOsRoutingPathSelector: path " + address);
            System.out.println("MinimumNetworkLoadOsRoutingPathSelector: bytes received " + bytesReceived);
            if (bytesReceived < bestBytesReceived) {
                bestPath = pathDescriptor;
                bestBytesReceived = bytesReceived;
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
                OsRoutingPathDescriptor pathDescriptor = selectPath(sourceNodeId, Integer.parseInt(destNode.getId()), null, null);
                paths.put(Integer.parseInt(destNode.getId()), pathDescriptor);
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
