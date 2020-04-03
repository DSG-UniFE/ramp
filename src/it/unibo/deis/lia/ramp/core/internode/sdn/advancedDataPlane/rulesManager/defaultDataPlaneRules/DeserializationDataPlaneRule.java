package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManagerInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Dmitrij David Padalino Montenero
 * <p>
 * This default DataPlaneRule prints to System.out the DataType payload of the packet
 * after packet deserialization.
 * The deserialization can be applied only for packets with payload size less than
 * {@link UnicastPacket#MAX_DELAY_TOLERANT_PAYLOAD} bytes for UnicastPacket
 * {@link BroadcastPacket#MAX_BROADCAST_PAYLOAD} bytes for BroadcastPacket
 * <p>
 * The packets with bigger payloads are not supported.
 */
public class DeserializationDataPlaneRule extends AbstractDataPlaneRule implements Serializable {

    private static final long serialVersionUID = -6549746290376459282L;

    public DeserializationDataPlaneRule() {

    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {
        Object payload = null;
        try {
            payload = E2EComm.deserialize(up.getBytePayload());
        } catch (Exception e) {
            e.printStackTrace();
        }

        DataTypesManagerInterface dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));

        String dataType = payload.getClass().getSimpleName();
        Class cls = dataTypesManager.getDataTypeClassObject(dataType);
        Class noparams[] = {};
        Method method = null;
        int seqNumber = -1;
        int payloadSize = -1;

        try {
            method = cls.getMethod("getSeqNumber", noparams);
            seqNumber = (int) method.invoke(payload);
            method = cls.getMethod("getPayloadSize", noparams);
            payloadSize = (int) method.invoke(payload);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        System.out.println("DeserializationDataPlaneRule: dataType: " + dataType + ", seqNumber: " + seqNumber + ", payloadSize " + payloadSize + ", from " + up.getSourceNodeId());
    }

    @Override
    public void applyRuleToBroadcastPacket(BroadcastPacket bp) {
        Object payload = null;
        try {
            payload = E2EComm.deserialize(bp.getBytePayload());
        } catch (Exception e) {
            e.printStackTrace();
        }

        DataTypesManagerInterface dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));

        String dataType = payload.getClass().getSimpleName();
        Class cls = dataTypesManager.getDataTypeClassObject(dataType);
        Class noparams[] = {};
        Method method = null;
        int seqNumber = -1;
        int payloadSize = -1;
        try {
            method = cls.getMethod("getSeqNumber", noparams);
            seqNumber = (int) method.invoke(payload);
            method = cls.getMethod("getPayloadSize", noparams);
            payloadSize = (int) method.invoke(payload);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        System.out.println("DeserializationDataPlaneRule: dataType: " + dataType + ", seqNumber: " + seqNumber + ", payloadSize " + payloadSize + ", from " + bp.getSourceNodeId());
    }
}
