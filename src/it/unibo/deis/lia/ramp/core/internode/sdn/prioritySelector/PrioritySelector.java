package it.unibo.deis.lia.ramp.core.internode.sdn.prioritySelector;

import java.util.Map;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;

/**
 * @author Alessandro Dolci
 */
public interface PrioritySelector {
    Map<Integer, Integer> getFlowPriorities(Map<Integer, Integer> currentFlowPriorities, Map<Integer, ApplicationRequirements> flowApplicationRequirements);
}