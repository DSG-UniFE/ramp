package it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient;

import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;

import java.util.List;

/**
 * @author Dmitrij David Padalino Montenero
 */
public interface ControllerClientInterface {
    List<PathDescriptor> getFlowMulticastNextHops(int flowId);

    public int getFlowPriority(int flowId);

    public String[] getFlowPath(int destNodeId, int flowId);
}
