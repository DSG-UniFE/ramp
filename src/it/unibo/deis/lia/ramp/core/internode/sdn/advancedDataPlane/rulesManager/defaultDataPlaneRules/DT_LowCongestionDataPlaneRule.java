package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManagerInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import it.unibo.deis.lia.ramp.util.rampUtils.RampUtilsInterface;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Matteo Mendula
 *
 *
 */
public class DT_LowCongestionDataPlaneRule extends AbstractDataPlaneRule implements Serializable {

    private static final long serialVersionUID = -2446589988154936840L;

    private long infoDataTypeId = -1631743108532280182L;
    private long videoDataTypeId = 7332943357871452826L;
    private long vibrationalDataTypeId = 793107902207408161L;

    private long infoDelay = 10;
    private long videoDelay = 50;
    private long vibrationalDelay = 50;

    private long infoDropProbability = 15;
    private long videoDropProbability = 15;
    private long vibrationalDropProbability = 15;

    private long[] dataTypeIdsToConsider = {videoDataTypeId,vibrationalDataTypeId,infoDataTypeId};

    private boolean droppingPacketsEnabled;
    private RampUtilsInterface rampUtils = null;
    private DataTypesManagerInterface dataTypesManager = null;
    private ControllerClientInterface controllerClient;

    public DT_LowCongestionDataPlaneRule() {
        this.droppingPacketsEnabled = true;
    }

    @Override
    public void applyRuleToUnicastHeader(UnicastHeader uh) {
        long dataTypeId = uh.getDataType();
        System.out.println("--> DT_LowCongestionDataPlaneRule is ON");
    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {

//        int randomNum = ThreadLocalRandom.current().nextInt(0, 10); //0-9

        UnicastHeader packetHeader = up.getHeader();
        long dataTypeId = packetHeader.getDataType();
        System.out.println("--> DT_LowCongestionDataPlaneRule is ON");
        //        if (packetHeader.getDataType() == provaDataTypeId) {
        if (Arrays.stream(dataTypeIdsToConsider).anyMatch(x -> x==dataTypeId)) {
            int pathLen = up.getDest().length;
            try{
                if(this.rampUtils == null) {
                    this.rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));
                }
                Object payload = this.rampUtils.deserialize(up.getBytePayload());
                if(this.dataTypesManager == null) {
                    this.dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));
                }
                if (this.controllerClient == null) {
                    this.controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
                }
                String dataType = payload.getClass().getSimpleName();
                Class cls = this.dataTypesManager.getDataTypeClassObject(dataType);

                //invoking method getSeqNum
                Method method = cls.getDeclaredMethod("getSeqNumber");
                method.setAccessible(true);
                int seqNumber = (int) method.invoke(payload);

                // invoking method get isDroppable
                method = cls.getMethod("getIsDroppable");
                method.setAccessible(true);
                boolean isDroppable = (boolean) method.invoke(payload);

                //invoking method isDelayable
                method = cls.getMethod("getDelayable");
                method.setAccessible(true);
                long maxDelay = (long) method.invoke(payload);

                this.rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));

                int randomNumberOverHundred= this.rampUtils.nextRandomInt(100);
                long finalDelay = 0;
                boolean shouldDrop = false;
                double dropProbability = 0;
                double dropProbabilityDependingOnType = 0;

                if (dataTypeId == infoDataTypeId){
                    finalDelay = Math.min(maxDelay, infoDelay);
                    dropProbabilityDependingOnType = infoDropProbability;
                }else if (dataTypeId == videoDataTypeId){
                    finalDelay = Math.min(maxDelay, videoDelay);
                    dropProbabilityDependingOnType = videoDropProbability;
                } else if (dataTypeId == vibrationalDataTypeId){
                    finalDelay = Math.min(maxDelay, vibrationalDelay);
                    dropProbabilityDependingOnType = vibrationalDropProbability;
                }

                for (int i=1; i <= pathLen; i++){
                    dropProbability += Math.pow(dropProbabilityDependingOnType * 1.0 /100, i);
                }
                shouldDrop = isDroppable && (Math.min(dropProbability*100, 90) > randomNumberOverHundred);
//                controllerClient.log("----------> seqnum " + seqNumber);

//                System.out.println("----------> dropProbability " + dropProbability + " randomNumberOverHundred "+ randomNumberOverHundred +
//                        " shouldDrop " + shouldDrop + " isDroppable " + isDroppable);



                if (this.droppingPacketsEnabled && shouldDrop){
//                if (randomNum != 0){
                    /*
                     * Drop the packet.
                     */
                    packetHeader.setDest(null);
                    up.setRetry((byte) 0);
                    System.out.println("DT_LowCongestionDataPlaneRule: dropping " + dataType + " packet with seqNumber: " + seqNumber);
                }else if (finalDelay > 0){
//                }else if (false){
                    /*
                     * Consider delay
                     */
                    System.out.println("DT_LowCongestionDataPlaneRule: delaying " + dataType + " packet with seqNumber: " + seqNumber);
                    try {
                        Thread.sleep(finalDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

            }catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            controllerClient.log("----------> ho trovato un tipo sconosciuto dataTypeId");
        }
    }

    @Override
    public void applyRuleToBroadcastPacket(BroadcastPacket bp) {
        long dataTypeId = bp.getDataType();
        System.out.println("DT_LowCongestionDataPlaneRule: dataTypeId: " + dataTypeId);
    }
}
