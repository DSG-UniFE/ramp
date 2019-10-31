package test.sdncontroller;

import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.graphUtils.GraphUtils;
import org.graphstream.graph.Graph;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class GraphTest {

    public static void main(String[] args) {
        Graph topologyGraph = GraphUtils.loadTopologyGraphFromDGSFile("./temp/graphTest/topologyGraph.dgs");

        /*
         * Configure graph for visualization and display
         */
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
        topologyGraph.addAttribute("ui.quality");
        topologyGraph.addAttribute("ui.antialias");
        topologyGraph.addAttribute("ui.stylesheet", "node {fill-color: blue; size: 40px; }");

        topologyGraph.display();
    }
}
