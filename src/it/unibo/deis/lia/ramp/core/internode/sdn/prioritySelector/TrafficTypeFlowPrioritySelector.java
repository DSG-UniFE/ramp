package it.unibo.deis.lia.ramp.core.internode.sdn.prioritySelector;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class TrafficTypeFlowPrioritySelector implements PrioritySelector {
    @Override
    public Map<Integer, Integer> getFlowPriorities(Map<Integer, Integer> currentFlowPriorities, Map<Integer, ApplicationRequirements> flowApplicationRequirements) {
        Map<Integer, Integer> flowPriorities = new ConcurrentHashMap<>();
        for (Integer flowId : flowApplicationRequirements.keySet()) {
            ApplicationRequirements applicationRequirements = flowApplicationRequirements.get(flowId);
            if (applicationRequirements.getTrafficType().equals(TrafficType.CONTROL_STREAM))
                /*
                 * This is hardcoded, so It won't never be called.
                 */
                flowPriorities.put(flowId, 0);
            else if (applicationRequirements.getTrafficType().equals(TrafficType.VIDEO_STREAM))
                flowPriorities.put(flowId, 1);
            else if (applicationRequirements.getTrafficType().equals(TrafficType.AUDIO_STREAM))
                flowPriorities.put(flowId, 2);
            else if (applicationRequirements.getTrafficType().equals(TrafficType.FILE_TRANSFER))
                flowPriorities.put(flowId, 3);
            else if (applicationRequirements.getTrafficType().equals(TrafficType.DEFAULT))
                flowPriorities.put(flowId, 4);
        }
        return flowPriorities;
    }
}

