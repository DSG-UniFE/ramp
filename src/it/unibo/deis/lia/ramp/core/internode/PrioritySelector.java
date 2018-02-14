package it.unibo.deis.lia.ramp.core.internode;

import java.util.Map;

public interface PrioritySelector {
	
	public Map<Integer, Integer> getFlowPriorities(Map<Integer, Integer> currentFlowPriorities, Map<Integer, ApplicationRequirements> flowApplicationRequirements);

}
