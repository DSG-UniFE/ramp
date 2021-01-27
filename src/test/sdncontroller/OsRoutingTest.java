package test.sdncontroller;

import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.graphUtils.GraphUtils;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.BreadthFirstOsRoutingPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.FewestIntersectionsOsRoutingPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.OsRoutingTopologyGraphSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;
import org.graphstream.graph.Graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class OsRoutingTest {

    public static void main(String[] args) {
        Graph topologyGraph = GraphUtils.loadTopologyGraphFromDGSFile("./temp/osRoutingTestTopology/topologyGraph.dgs");
        //OsRoutingTopologyGraphSelector pathSelector = new BreadthFirstOsRoutingPathSelector(topologyGraph);
        OsRoutingTopologyGraphSelector pathSelector = new FewestIntersectionsOsRoutingPathSelector(topologyGraph);
        OsRoutingTopologyGraphSelector pathSelector2 = new BreadthFirstOsRoutingPathSelector(topologyGraph);
        //TopologyGraphSelector pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);

        String[] path1 = new String[2];
        path1[0] = "192.168.1.2";
        path1[1] = "192.168.1.8";
        List<Integer> pathNodesIds1 = new ArrayList<>();
        pathNodesIds1.add(-343180451);
        pathNodesIds1.add(-1005300814);
        OsRoutingPathDescriptor pt1 = new OsRoutingPathDescriptor(path1, pathNodesIds1, "192.168.1.14");

        String[] path2 = new String[1];
        path2[0] = "192.168.1.2";
        List<Integer> pathNodesIds2 = new ArrayList<>();
        pathNodesIds2.add(-343180451);
        OsRoutingPathDescriptor pt2 = new OsRoutingPathDescriptor(path2, pathNodesIds2, "192.168.1.14");

        String[] path3 = new String[1];
        path3[0] = "10.42.0.225";
        List<Integer> pathNodesIds3 = new ArrayList<>();
        pathNodesIds3.add(-343180451);
        OsRoutingPathDescriptor pt3 = new OsRoutingPathDescriptor(path3, pathNodesIds3, "10.42.0.1");

        String[] path4 = new String[2];
        path4[0] = "192.168.1.2";
        path4[1] = "192.168.2.1";
        List<Integer> pathNodesIds4 = new ArrayList<>();
        pathNodesIds4.add(-343180451);
        pathNodesIds4.add(-1005300814);
        OsRoutingPathDescriptor pt4 = new OsRoutingPathDescriptor(path4, pathNodesIds4, "192.168.1.14");

        String[] path5 = new String[2];
        path5[0] = "10.42.0.1";
        path5[1] = "192.168.1.8";
        List<Integer> pathNodesIds5 = new ArrayList<>();
        pathNodesIds5.add(-343180451);
        pathNodesIds5.add(-1005300814);
        OsRoutingPathDescriptor pt5 = new OsRoutingPathDescriptor(path5, pathNodesIds5, "10.42.0.1");

//        HashMap<Integer, OsRoutingPathDescriptor> activePaths = new HashMap<>();
//        HashMap<Integer, PathDescriptor> activePaths = new HashMap<>();
//        activePaths.put(1,pt1);
//          activePaths.put(2,pt2);
//        activePaths.put(3, pt3);
//        activePaths.put(4, pt4);
//        activePaths.put(5, pt5);


        int sourceNodeId = 1354517529;
        //int destNodeId = -1005300814;
        int destNodeId = -343180451;

        HashMap<Integer, OsRoutingPathDescriptor> activeForwardPaths = new HashMap<>();
        HashMap<Integer, OsRoutingPathDescriptor> activeBackwardPaths = new HashMap<>();

        OsRoutingPathDescriptor fp1 = pathSelector2.selectPath(sourceNodeId, destNodeId, null, activeForwardPaths);
        OsRoutingPathDescriptor bp1 = pathSelector2.reversePath(fp1);

        activeForwardPaths.put(1, fp1);
        activeBackwardPaths.put(1, bp1);

        OsRoutingPathDescriptor fp2 = pathSelector.selectPath(sourceNodeId, destNodeId, null, activeForwardPaths);
        OsRoutingPathDescriptor bp2temp = pathSelector.reversePath(fp2);

        HashMap<Integer, OsRoutingPathDescriptor> tempBackwardPaths = new HashMap<>(activeBackwardPaths);
        tempBackwardPaths.put(-1, bp2temp);

        OsRoutingPathDescriptor bp2 = pathSelector.selectPath(destNodeId, sourceNodeId, null, tempBackwardPaths);

        System.out.println("END");
    }
}
