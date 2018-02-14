package it.unibo.deis.lia.ramp.core.internode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApplicationTypeFlowPrioritySelector implements PrioritySelector {

	@Override
	public Map<Integer, Integer> getFlowPriorities(Map<Integer, Integer> currentFlowPriorities,
			Map<Integer, ApplicationRequirements> flowApplicationRequirements) {
		Map<Integer, Integer> flowPriorities = new ConcurrentHashMap<Integer, Integer>();
		for (Integer flowId : flowApplicationRequirements.keySet()) {
			ApplicationRequirements applicationRequirements = flowApplicationRequirements.get(flowId);
			if (applicationRequirements.getApplicationType().equals(ApplicationRequirements.ApplicationType.VIDEO_STREAM))
				flowPriorities.put(flowId, 0);
			else if (applicationRequirements.getApplicationType().equals(ApplicationRequirements.ApplicationType.AUDIO_STREAM))
				flowPriorities.put(flowId, 1);
			else if (applicationRequirements.getApplicationType().equals(ApplicationRequirements.ApplicationType.FILE_TRANSFER))
				flowPriorities.put(flowId, 2);
			else if (applicationRequirements.getApplicationType().equals(ApplicationRequirements.ApplicationType.DEFAULT))
				flowPriorities.put(flowId, 3);
		}
		return flowPriorities;
	}

}
