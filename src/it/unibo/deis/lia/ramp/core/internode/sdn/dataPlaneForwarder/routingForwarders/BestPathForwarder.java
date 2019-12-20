package it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.routingForwarders;

import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.DataPlaneForwarder;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;

import java.util.Arrays;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class BestPathForwarder implements DataPlaneForwarder {

    private static BestPathForwarder flowPathChanger = null;

    /**
     * Control flow ID value, to be used for control communications between ControllerService and ControllerClients.
     */
    private static final int CONTROL_FLOW_ID = 0;

    private ControllerClientInterface controllerClient = null;

    private final Semaphore fixRequestPermit = new Semaphore(1);

    private Map<Integer, String[]> flowPathCache;

    private Map<Integer, Long> flowPathCacheLastUpdate;

    private UpdateManager updateManager;

    private BestPathForwarder() {
        this.flowPathCache = new ConcurrentHashMap<>();
        this.flowPathCacheLastUpdate = new ConcurrentHashMap<>();
        this.updateManager = new UpdateManager();
        this.updateManager.start();
    }

    private void destroy() {
        this.updateManager.stopUpdateManager();
        this.flowPathCache = null;
        this.flowPathCacheLastUpdate = null;
        this.controllerClient = null;
    }

    public synchronized static BestPathForwarder getInstance() {
        if (flowPathChanger == null) {
            flowPathChanger = new BestPathForwarder();
            Dispatcher.getInstance(false).addPacketForwardingListener(flowPathChanger);
            System.out.println("BestPathForwarder ENABLED");
        }
        return flowPathChanger;
    }

    public void deactivate() {
        if (flowPathChanger != null) {
            Dispatcher.getInstance(false).removePacketForwardingListener(flowPathChanger);
            destroy();
            flowPathChanger = null;
            System.out.println("BestPathForwarder DISABLED");
        }
    }

    @Override
    public void receivedUdpUnicastPacket(UnicastPacket up) {
        receivedTcpUnicastPacket(up);
    }

    @Override
    public void receivedUdpBroadcastPacket(BroadcastPacket bp) {

    }

    @Override
    public void receivedTcpUnicastPacket(UnicastPacket up) {
        receivedTcpUnicastHeader(up.getHeader());
    }

    @Override
    public void receivedTcpUnicastHeader(UnicastHeader uh) {
        if (controllerClient == null) {
            controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
        }
        /*
         * Check if the packet header contains a valid flowId and has to be processed according to the SDN paradigm.
         * TODO Understand if the CONTROL MESSAGES must be supported by this forwarder.
         */
        int flowId = uh.getFlowId();
        if (flowId != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID) {
            String[] newPath;

            /*
             * If the current node is the sender, check for a new complete path
             */
            if (uh.getSourceNodeId() == Dispatcher.getLocalRampId() || uh.getCurrentHop() == (byte) 0) {
                newPath = controllerClient.getFlowPath(uh.getDestNodeId(), flowId);
                String[] test = uh.getDest();
                /*
                 * If the received path is not null and it's different from the current path,
                 * replace the current one with it.
                 */
                if (newPath != null && !Arrays.equals(newPath, uh.getDest())) {
                    uh.setDest(newPath);
                    System.out.println("BestPathForwarder: dest path changed to packet " + uh.getId() + " belonging to flow " + flowId);
                    for (int i = 0; i < newPath.length; i++)
                        System.out.println("FlowPathChanger: new flow path address " + i + ", " + newPath[i]);
                } else
                    System.out.println("BestPathForwarder: no changes made to packet " + uh.getId() + " belonging to flow " + flowId);
            }
        }
    }

    @Override
    public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {

    }

    @Override
    public void receivedTcpBroadcastPacket(BroadcastPacket bp) {

    }

    @Override
    public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
        sendingTcpUnicastHeaderException(up.getHeader(), e);
    }

    @Override
    public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
        if (controllerClient == null) {
            controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
        }

        int flowId = uh.getFlowId();
        if (flowId != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID) {
            int localRampId = Dispatcher.getLocalRampId();
            int sourceNodeId = uh.getSourceNodeId();
            int destNodeId = uh.getDestNodeId();

            if (destNodeId != localRampId) {
                /*
                 * Check Cache first
                 */
                if (this.flowPathCache.containsKey(flowId)) {
                    /*
                     * Retrieve the path from cache and set it
                     * in the packet header.
                     */
                    String[] newPath = flowPathCache.get(flowId);
                    uh.setDest(newPath);
                } else {
                    if (this.fixRequestPermit.tryAcquire()) {
                        /*
                         * Fix the broken path with a new one and
                         * push it to the original sender.
                         */
                        PathDescriptor fixedPathDescriptor = controllerClient.sendFixPathRequest(sourceNodeId, destNodeId, flowId);

                        /*
                         * If the sender node has discovered the broken path we need to
                         * completely replace the path previously calculated.
                         */
                        String[] newPath = fixedPathDescriptor.getPath();
                        ;
                        if (sourceNodeId != localRampId) {
                            /*
                             * If a node in the middle has discovered the broken path it must
                             * keep the history of the previous hops that the packet has already
                             * traversed and append the ones retrieved by the controller.
                             */
                            String[] currentPath = uh.getDest();
                            /*
                             * Find the local node IP address in the currentPath
                             */
                            Vector<String> localAddresses;
                            try {
                                localAddresses = Dispatcher.getLocalNetworkAddresses();
                                int localAddressIndex;
                                for (localAddressIndex = 0; localAddressIndex < currentPath.length; localAddressIndex++) {
                                    if (localAddresses.contains(currentPath[localAddressIndex])) {
                                        break;
                                    }
                                }

                                int currentPathOffset = localAddressIndex + 1;
                                int newPathLen = newPath.length;
                                String[] fixedPath = new String[currentPathOffset + newPathLen];
                                System.arraycopy(currentPath, 0, fixedPath, 0, currentPathOffset);
                                System.arraycopy(newPath, 0, fixedPath, currentPathOffset, newPathLen);
                                newPath = fixedPath;
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                        /*
                         * Save the new path in cache and set it in the packet header.
                         */
                        flowPathCache.put(flowId, newPath);
                        flowPathCacheLastUpdate.put(flowId, System.currentTimeMillis());
                        uh.setDest(newPath);

                        fixRequestPermit.release();
                    } else {
                        while(fixRequestPermit.availablePermits() == 0) {
                            /*
                             * Wait
                             */
                        }
                        /*
                         * Retrieve the path from cache and set it
                         * in the packet header.
                         */
                        String[] newPath = flowPathCache.get(flowId);
                        uh.setDest(newPath);
                    }
                }
            }
        }
    }

    /**
     * This Update Manager is an optimization useful in case of
     * detection of broken links. As soon as the BestPathForwarder
     * discovers a broken link it asks for a new valid path
     * and stores it for a certain amount of time in order to
     * avoid an overloading of FIX_PATH_REQUEST to the
     * ControllerService.
     */
    private class UpdateManager extends Thread {
        /**
         * Cache refresh time in seconds.
         */
        private static final int CACHE_REFRESH_TIME = 2 * 1000;

        /**
         * Cache validity time interval in seconds.
         */
        private static final int CACHE_VALIDITY_TIME = 2 * 1000;

        /**
         * Boolean value that reports if the CacheManager is currently active.
         */
        private boolean active;

        UpdateManager() {
            this.active = true;
        }

        public void stopUpdateManager() {
            System.out.println("BestPathForwarder CacheManager STOP");
            this.active = false;
        }

        private void updateCache() {
            if (flowPathCache != null) {
                for (Integer flowId : flowPathCache.keySet()) {
                    long flowCacheStartTime = flowPathCacheLastUpdate.get(flowId);
                    long elapsed = System.currentTimeMillis() - flowCacheStartTime;
                    if (elapsed > CACHE_VALIDITY_TIME) {
                        flowPathCache.remove(flowId);
                        flowPathCacheLastUpdate.remove(flowId);
                    }
                }
            }
        }

        @Override
        public void run() {
            System.out.println("BestPathForwarder CacheManager START");
            while (this.active) {
                try {
                    Thread.sleep(CACHE_REFRESH_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateCache();
            }
            System.out.println("BestPathForwarder CacheManager FINISHED");
        }
    }
}
