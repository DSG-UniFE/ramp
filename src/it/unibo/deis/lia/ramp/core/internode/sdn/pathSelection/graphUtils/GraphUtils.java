package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.graphUtils;

import java.io.*;

import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkDGS;
import org.graphstream.stream.file.FileSourceDGS;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This class contains general utils to deal with graphs provided by the GraphStream library.
 */
public final class GraphUtils {

    private GraphUtils() {
        throw new UnsupportedOperationException();
    }

    public static synchronized File saveTopologyGraphIntoDGSFile(Graph graph, String fileName) {
        File dgsFile = new File(fileName);
        try {
            if (dgsFile.createNewFile()) {
                System.out.println("File created: " + dgsFile.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        FileSinkDGS fs = new FileSinkDGS();
        try {
            fs.writeAll(graph, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dgsFile;
    }

    public static synchronized MultiGraph loadTopologyGraphFromDGSFile(String fileName) {
        MultiGraph loadedGraph = new MultiGraph("TopologyGraph");
        FileSourceDGS fs = new FileSourceDGS();
        fs.addSink(loadedGraph);
        try {
            fs.readAll(fileName);
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        return loadedGraph;
    }
}
