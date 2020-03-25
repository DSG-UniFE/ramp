package it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient;

import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;

import java.util.List;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This interface exposes one the methods available on the ControllerClient
 *
 * The class that implements these methods should be retrieved
 * using the ComponentLocator object.
 *
 * Example of usage:
 * ControllerClientInterface controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
 */
public interface ControllerClientInterface {
    List<PathDescriptor> getFlowMulticastNextHops(int flowId);

    int getFlowPriority(int flowId);

    String[] getFlowPath(int destNodeId, int flowId);

    PathDescriptor sendNewPathRequest(int destNodeId, PathSelectionMetric pathSelectionMetric);

    /**
     * This method lets any Controller Client Node to ask
     * the Controller Service a new path calculation for
     * an existing flowId given a sourceNodeId and a destNodeId.
     *
     * In general when a ControllerClient asks for a new path
     * we assume that the sourceNodeId is the current Controller Client,
     * thanks to this method it is possible to make the Controller Service
     * computing a new path having a sourceNodeId different from the
     * current node. This is very useful in case a node in the middle
     * discovers that the precomputed path is not working anymore
     * and wants to fix it and inform the original sender of the new path
     * for that flowId.
     *
     * @param sourceNodeId RAMP Id of the source
     * @param destNodeId RAMP Id of the destination
     * @param flowId of the communication
     * @return the fixed path
     */
    PathDescriptor sendFixPathRequest(int sourceNodeId, int destNodeId, int flowId);

    int sendOsRoutingUpdatePriorityRequest(int routeId, boolean switchToOsRouting);

    /*
     * TODO Remove me
     */
    void log(String message);

    /*
     * TODO Remove me
     */
    void logRule(String message);
}
