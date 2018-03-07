package it.unibo.deis.lia.ramp.core.internode;

import java.util.Map;


/**
 * 
 * @author Alessandro Dolci
 *
 */
public interface TopologyGraphSelector {
	
	public PathDescriptor selectPath(int sourceNodeId, int destNodeId, ApplicationRequirements applicationRequirements, Map<Integer, PathDescriptor> activePaths);
	public Map<Integer, PathDescriptor> getAllPathsFromSource(int sourceNodeId);

	public enum PathSelectionMetric {
		BREADTH_FIRST,
		FEWEST_INTERSECTIONS,
		MINIMUM_LOAD
	}
}
