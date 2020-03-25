package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManagerInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import it.unibo.deis.lia.ramp.util.rampUtils.RampUtilsInterface;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Vector;

/**
 * @author Dmitrij David Padalino Montenero
 * DataPlaneRule to handle packets belonging to VibrationDataType class
 * {@link it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType}
 */
public class VibrationDataPlaneRule extends AbstractDataPlaneRule implements Serializable {

    private static final long serialVersionUID = 6906201771790644794L;

    private long vibrationDataTypeId = 793107902207408161L;

    private int dropPacketVibrationValueThreshold = 10;

    private int fastestPathPacketsVibrationValueThreshold = 20;

    private DataTypesManagerInterface dataTypesManager = null;

    private RampUtilsInterface rampUtils = null;

    private ControllerClientInterface controllerClient = null;

    private boolean droppingPacketsEnabled;

    private boolean newFastestPathAvailable;

    private boolean semaphore;

    private String[] newFastestPath;

    public VibrationDataPlaneRule() {
        this.droppingPacketsEnabled = true;
        this.newFastestPathAvailable = false;
        this.semaphore = true;
    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {
        UnicastHeader packetHeader = up.getHeader();
        if (packetHeader.getDataType() == vibrationDataTypeId) {
            try {
                if(this.rampUtils == null) {
                    this.rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));
                }
                Object payload = this.rampUtils.deserialize(up.getBytePayload());
                if(this.dataTypesManager == null) {
                    this.dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));
                }

                String dataType = payload.getClass().getSimpleName();
                Class cls = this.dataTypesManager.getDataTypeClassObject(dataType);

                Method method = cls.getDeclaredMethod("getVibrationValue");
                method.setAccessible(true);
                int vibrationValue = (int) method.invoke(payload);

                method = cls.getMethod("getSeqNumber");
                method.setAccessible(true);
                int seqNumber = (int) method.invoke(payload);

                if (vibrationValue <= this.dropPacketVibrationValueThreshold && this.droppingPacketsEnabled) {
                    /*
                     * Drop 90% of the packets, to do so
                     * we use a secure random retrieved using RampEntryPoint
                     * that gives use a number between 0 and 9.
                     */
                    this.rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));

                    int probability = this.rampUtils.nextRandomInt(10);
                    if (probability < 9) {
                        /*
                         * Drop the packet.
                         */
                        packetHeader.setDest(null);
                        up.setRetry((byte) 0);
                        System.out.println("VibrationDataPlaneRule: dropping " + dataType + " packet with seqNumber: " + seqNumber);
                    }
                } else if (vibrationValue <= this.fastestPathPacketsVibrationValueThreshold) {
                    this.droppingPacketsEnabled = false;
                }
                else if (!this.newFastestPathAvailable && this.semaphore) {
                    if(droppingPacketsEnabled) {
                        this.droppingPacketsEnabled = false;
                    }
                    /*
                     * If vibrationValue > this.fastestPathPacketsVibrationValueThreshold
                     */

                    /*
                     * We want that the new fastest path is computed only one time.
                     * While the new path computation is in progress, if new packets
                     * arrive they will still follow the slow path until the fastest
                     * one will be available.
                     */
                    this.semaphore = false;
                    /*
                     * Discover the fastest path available
                     * We need to contact the SDNController to trigger the new path calculation
                     */
                    findNewFastestPath(packetHeader);
                }

                /*
                 * Set the new fastest path in the header if available.
                 */
                if(this.newFastestPathAvailable) {
                    up.setDest(this.newFastestPath);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void findNewFastestPath(UnicastHeader uh) {
        if(this.rampUtils == null) {
            this.rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));
        }
        int localRampId = this.rampUtils.getNodeId();
        int sourceNodeId = uh.getSourceNodeId();
        int destNodeId = uh.getDestNodeId();

        if(destNodeId != localRampId) {
            if (this.controllerClient == null) {
                this.controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
            }

            long pre = System.currentTimeMillis();
            PathDescriptor fastestPathDescriptor = this.controllerClient.sendNewPathRequest(destNodeId, PathSelectionMetric.BREADTH_FIRST);
            long post = System.currentTimeMillis();

            controllerClient.log("VibrationDataPlaneRule: app level switch protocol completed in " + (post-pre) + "milliseconds");

            /*
             * If the sender node has discovered the fastest path we need to
             * completely replace the current slow path.
             */
            String[] newPath = fastestPathDescriptor.getPath();

            if(sourceNodeId != localRampId) {
                /*
                 * If a node in the middle has discovered the fastest path it must
                 * keep the history of the previous hops that the packet has already
                 * traversed and append the one retrieved by the controller.
                 */
                String[] currentPath = uh.getDest();
                /*
                 * Find the local node IP address in the currentPath
                 */
                Vector<String> localAddresses;
                try {
                    localAddresses = this.rampUtils.getLocalNetworkAddresses();
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

            this.newFastestPath = newPath;
            this.newFastestPathAvailable = true;
        }
    }
}
