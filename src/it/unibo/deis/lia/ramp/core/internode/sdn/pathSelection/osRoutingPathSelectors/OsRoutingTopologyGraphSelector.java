package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;

import java.util.Map;

/**
 * @author Dmitrij David Padalino Montenero
 */
public interface OsRoutingTopologyGraphSelector {
    OsRoutingPathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, OsRoutingPathDescriptor> activePaths);
    Map<Integer, OsRoutingPathDescriptor> getAllPathsFromSource(int sourceNodeId);
    OsRoutingPathDescriptor reversePath(OsRoutingPathDescriptor path);
}
