package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiNode;

import java.util.*;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This is class is a modified version of BreadthFirstFlowPathSelector able to let the
 * existence of multiple breadth first paths exploiting all the network interface available.
 * This path selector works only in case of OsRouting.
 */
public class BreadthFirstOsRoutingPathSelector implements OsRoutingTopologyGraphSelector {

    private Graph topologyGraph;

    public BreadthFirstOsRoutingPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
    }

    @Override
    public OsRoutingPathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, OsRoutingPathDescriptor> activePaths) {
        String sourceIp = null;
        String destinationIp = null;
        List<String> bestPath = new ArrayList<String>();
        List<Integer> bestPathNodeIds = new ArrayList<Integer>();
        boolean found = false;
        /*
         * Data structure to hold mappings between graph IDs of nodes and their parents (node, parent)
         */
        Map<String, String> parentNodes = new HashMap<String, String>();
        /*
         * FIFO managed list to hold the sequence of parent nodes.
         */
        LinkedList<MultiNode> nextParentNodes = new LinkedList<MultiNode>();

        MultiNode sourceNode = this.topologyGraph.getNode(Integer.toString(sourceNodeId));
        Iterator<MultiNode> iterator = sourceNode.getBreadthFirstIterator(false);

        MultiNode destinationNode = null;

        MultiNode parentNode = iterator.next();
        MultiNode node = null;
        MultiNode firstHopNode = null;
        MultiNode lastHopNode = null;
        int pointedNodes = 0;
        Iterator<MultiNode> neighborNodeIterator = parentNode.getNeighborNodeIterator();
        while (neighborNodeIterator.hasNext()) {
            neighborNodeIterator.next();
            pointedNodes++;
        }
        System.out.println("BreadthFirstOsRoutingPathSelector: first node, " + parentNode.getId());

        /*
         * Iterate until a path to destNode is found.
         */
        while (iterator.hasNext() && !found) {
            node = iterator.next();
            System.out.println("BreadthFirstOsRoutingPathSelector: next node, " + node.getId());
            /*
             * Check that the current node hasn't already been visited
             */
            if (parentNodes.containsKey(node.getId()) == false &&
                    parentNodes.containsValue(node.getId()) == false) {
                parentNodes.put(node.getId(), parentNode.getId());
                /*
                 * If the current node is destNode, backtrack and build the path using the parentNodes structure.
                 */
                if (node.getId().equals(Integer.toString(destNodeId))) {
                    /*
                     * Set the destination address to use.
                     */
                    destinationNode = node;
                    lastHopNode = this.topologyGraph.getNode(parentNodes.get(destinationNode.getId()));
                    bestPathNodeIds.add(Integer.parseInt(destinationNode.getId()));

                    node = lastHopNode;
                    while (node != sourceNode) {
                        parentNode = this.topologyGraph.getNode(parentNodes.get(node.getId()));

                        /*
                         * If there are multiple edges connecting the two nodes, simply select the first one.
                         */
                        Edge parentEdge = node.getEdgeBetween(parentNode);

                        String address = parentEdge.getAttribute("address_" + node.getId());
                        /*
                         * If the path contains a null address, return null.
                         */
                        if (address == null)
                            return null;

                        bestPath.add(address);
                        bestPathNodeIds.add(Integer.parseInt(node.getId()));
                        System.out.println("BreadthFirstOsRoutingPathSelector: adding address " + parentEdge.getAttribute("address_" + node.getId()) + " of node " + node.getId());
                        if (parentNode == sourceNode) {
                            firstHopNode = node;
                        }
                        node = parentNode;
                    }
                    if (firstHopNode == null) {
                        firstHopNode = destinationNode;
                    }

                    bestPathNodeIds.add(Integer.parseInt(sourceNode.getId()));
                    Collections.reverse(bestPathNodeIds);

                    /*
                     * Detect the source and the destination IP Addresses To Use
                     */
                    List<OsRoutingPathDescriptor> sourceDestinationActivePaths = new ArrayList<>();
                    for (OsRoutingPathDescriptor activePathDescriptor : activePaths.values()) {
                        if (activePathDescriptor.getPathNodeIds().equals(bestPathNodeIds)) {
                            sourceDestinationActivePaths.add(activePathDescriptor);
                        }
                    }

                    if (sourceDestinationActivePaths.size() > 0) {
                        /*
                         * In case of oneHopRoute
                         */
                        if (bestPathNodeIds.size() == 2) {
                            /*
                             * Discover the sourceIp to be used
                             */
                            Collection<Edge> oneHopEdges = sourceNode.getEdgeSetBetween(firstHopNode);
                            boolean sourceDestinationFound = false;

                            for (Edge candidateEdge : oneHopEdges) {
                                /*
                                 * TODO Improve
                                 *  In case the destination is connected to the same network via two
                                 *  interfaces this solution does not work, because we assume that both the source and the
                                 *  destination are connected to the same network only with one interface. We need to cover this
                                 *  case too. At the moment this is not needed for testing.
                                 */
                                String candidateSourceIp = candidateEdge.getAttribute("address_" + sourceNode.getId());
                                if (candidateSourceIp == null) {
                                    continue;
                                }
                                boolean candidateSourceAlreadyUsed = false;

                                for (OsRoutingPathDescriptor activePathDescriptor : sourceDestinationActivePaths) {
                                    if (activePathDescriptor.getSourceIp().equals(candidateSourceIp)) {
                                        candidateSourceAlreadyUsed = true;
                                        break;
                                    }
                                }

                                if (!candidateSourceAlreadyUsed) {
                                    sourceIp = candidateSourceIp;
                                    /*
                                     * Find the destinationIp according to the sourceIp found, make sure they belong
                                     * to the same subnet.
                                     */
                                    destinationIp = candidateEdge.getAttribute("address_" + destinationNode.getId());
                                    bestPath.add(0, destinationIp);
                                    sourceDestinationFound = true;
                                    break;
                                }
                            }

                            if (!sourceDestinationFound) {
                                return null;
                            }
                        } else if (bestPathNodeIds.size() > 2) {
                            /*
                             * In case of multiHopRoute Discover the sourceIp to be used
                             */
                            Collection<Edge> firstHopEdges = sourceNode.getEdgeSetBetween(firstHopNode);
                            Collection<Edge> lastHopEdges = lastHopNode.getEdgeSetBetween(destinationNode);
                            boolean sourceDestinationFound = false;

                            for (Edge candidateFirstHopEdge : firstHopEdges) {
                                String candidateSourceIp = candidateFirstHopEdge.getAttribute("address_" + sourceNode.getId());
                                if (candidateSourceIp == null) {
                                    continue;
                                }

                                boolean candidateSourceAlreadyUsed = false;

                                for (OsRoutingPathDescriptor activePathDescriptor : sourceDestinationActivePaths) {
                                    if (activePathDescriptor.getSourceIp().equals(candidateSourceIp)) {
                                        candidateSourceAlreadyUsed = true;
                                        break;
                                    }
                                }

                                if (!candidateSourceAlreadyUsed) {
                                    sourceIp = candidateSourceIp;
                                    bestPath.set((bestPath.size() - 1), candidateFirstHopEdge.getAttribute("address_" + firstHopNode.getId()));

                                    for (Edge candidateLastHopEdge : lastHopEdges) {
                                        String candidateDestinationIp = candidateLastHopEdge.getAttribute("address_" + destinationNode.getId());
                                        if (candidateDestinationIp == null) {
                                            continue;
                                        }
                                        destinationIp = candidateDestinationIp;
                                        bestPath.add(0, destinationIp);
                                        sourceDestinationFound = true;
                                        break;
                                    }

                                    if (sourceDestinationFound) {
                                        break;
                                    }
                                } else {
                                    /*
                                     * If this sourceIp is already used let's discover if exists
                                     * another destinationIpAvailable
                                     */
                                    for (Edge candidateLastHopEdge : lastHopEdges) {
                                        String candidateDestinationIp = candidateLastHopEdge.getAttribute("address_" + destinationNode.getId());
                                        if (candidateDestinationIp == null) {
                                            continue;
                                        }
                                        boolean candidateDestinationAlreadyUsed = false;

                                        for (OsRoutingPathDescriptor activePathDescriptor : sourceDestinationActivePaths) {
                                            if (activePathDescriptor.getSourceIp().equals(candidateSourceIp) && activePathDescriptor.getDestinationIP().equals(candidateDestinationIp)) {
                                                candidateDestinationAlreadyUsed = true;
                                                break;
                                            }
                                        }

                                        if (!candidateDestinationAlreadyUsed) {
                                            sourceIp = candidateSourceIp;
                                            destinationIp = candidateDestinationIp;
                                            bestPath.add(0, destinationIp);
                                            sourceDestinationFound = true;
                                            break;
                                        }
                                    }
                                    if (sourceDestinationFound) {
                                        break;
                                    }
                                }
                            }
                            if (!sourceDestinationFound) {
                                return null;
                            }
                        }
                    } else {
                        Edge firstHopEdge = sourceNode.getEdgeBetween(firstHopNode);
                        sourceIp = firstHopEdge.getAttribute("address_" + sourceNode.getId());
                        if (sourceIp == null)
                            return null;

                        Edge lastHopEdge = lastHopNode.getEdgeBetween(destinationNode);
                        destinationIp = lastHopEdge.getAttribute("address_" + destinationNode.getId());
                        if (destinationIp == null)
                            return null;
                        bestPath.add(0, destinationIp);
                    }

                    Collections.reverse(bestPath);
                    /*
                     * Breadth-first search ensures that the first path found is the shortest.
                     */
                    found = true;
                }
                nextParentNodes.add(node);
            }
            pointedNodes--;
            /*
             * Check if every pointed node of the current parent has been explored, if so, select the next parent node.
             */
            if (pointedNodes == 0) {
                parentNode = nextParentNodes.remove();
                neighborNodeIterator = parentNode.getNeighborNodeIterator();
                while (neighborNodeIterator.hasNext()) {
                    neighborNodeIterator.next();
                    pointedNodes++;
                }
                iterator = parentNode.getBreadthFirstIterator(false);
                parentNode = iterator.next();
            }
        }
        return new OsRoutingPathDescriptor(bestPath.toArray(new String[0]), bestPathNodeIds, sourceIp);
    }

    @Override
    public Map<Integer, OsRoutingPathDescriptor> getAllPathsFromSource(int sourceNodeId) {
        Map<Integer, OsRoutingPathDescriptor> paths = new HashMap<>();
        for (Node destNode : this.topologyGraph.getNodeSet()) {
            int destNodeId = Integer.parseInt(destNode.getId());
            if (destNodeId != sourceNodeId) {
                /*
                 * The paths to select are for default applications, so applicationRequirements is null.
                 */
                OsRoutingPathDescriptor path = selectPath(sourceNodeId, destNodeId, null, null);
                if (path != null)
                    paths.put(destNodeId, path);
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
