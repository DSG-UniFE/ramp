package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathSelectors;

import java.util.*;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.TopologyGraphSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiNode;

/**
 * @author Alessandro Dolci
 */
public class BreadthFirstFlowPathSelector implements TopologyGraphSelector {

    private Graph topologyGraph;

    public BreadthFirstFlowPathSelector(Graph topologyGraph) {
        this.topologyGraph = topologyGraph;
    }

    @Override
    public PathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, PathDescriptor> activePaths) {
        List<String> bestPath = new ArrayList<>();
        List<Integer> bestPathNodeIds = new ArrayList<>();
        boolean found = false;
        /*
         * Data structure to hold mappings between graph IDs of nodes and their parents (node, parent)
         */
        Map<String, String> parentNodes = new HashMap<>();
        /*
         * FIFO managed list to hold the sequence of parent nodes.
         */
        LinkedList<MultiNode> nextParentNodes = new LinkedList<>();

        MultiNode sourceNode = this.topologyGraph.getNode(Integer.toString(sourceNodeId));
        Iterator<MultiNode> iterator = sourceNode.getBreadthFirstIterator(false);

        MultiNode parentNode = iterator.next();
        MultiNode node;
        int pointedNodes = 0;
        Iterator<MultiNode> neighborNodeIterator = parentNode.getNeighborNodeIterator();
        while (neighborNodeIterator.hasNext()) {
            neighborNodeIterator.next();
            pointedNodes++;
        }
        System.out.println("BreadthFirstFlowPathSelector: first node, " + parentNode.getId());

        /*
         * Iterate until a path to destNode is found.
         */
        while (iterator.hasNext() && !found) {
            node = iterator.next();
            System.out.println("BreadthFirstFlowPathSelector: next node, " + node.getId());
            /*
             * Check that the current node hasn't already been visited
             */
            if (!parentNodes.containsKey(node.getId()) && !parentNodes.containsValue(node.getId())) {
                parentNodes.put(node.getId(), parentNode.getId());
                /*
                 * If the current node is destNode, backtrack and build the path using the parentNodes structure.
                 */
                if (node.getId().equals(Integer.toString(destNodeId))) {
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
                        System.out.println("BreadthFirstFlowPathSelector: adding address " + parentEdge.getAttribute("address_" + node.getId()) + " of node " + node.getId());
                        node = parentNode;
                    }
                    Collections.reverse(bestPath);
                    Collections.reverse(bestPathNodeIds);
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
        return new PathDescriptor(bestPath.toArray(new String[0]), bestPathNodeIds);
    }

    @Override
    public Map<Integer, PathDescriptor> getAllPathsFromSource(int sourceNodeId) {
        Map<Integer, PathDescriptor> paths = new HashMap<>();
        for (Node destNode : this.topologyGraph.getNodeSet()) {
            int destNodeId = Integer.parseInt(destNode.getId());
            if (destNodeId != sourceNodeId) {
                /*
                 * The paths to select are for default applications, so applicationRequirements is null.
                 */
                PathDescriptor path = selectPath(sourceNodeId, destNodeId, null, null);
                if (path != null)
                    paths.put(destNodeId, path);
            }
        }
        return paths;
    }
}
