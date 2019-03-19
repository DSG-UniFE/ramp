package it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;

import java.util.Map;

/**
 * @author Alessandro Dolci
 */
public interface TopologyGraphSelector {
    PathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, PathDescriptor> activePaths);
    Map<Integer, PathDescriptor> getAllPathsFromSource(int sourceNodeId);
}
