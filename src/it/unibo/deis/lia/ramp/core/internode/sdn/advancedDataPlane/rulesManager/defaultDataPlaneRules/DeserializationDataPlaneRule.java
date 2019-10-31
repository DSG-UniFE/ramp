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
        double[] array1 = null;
        double[] array2 = null;
        double[] array3 = null;
        double[] array4 = null;
        double[] array5 = null;
        double[] array6 = null;
        double[] array7 = null;
        double[] array8 = null;
        double[] array9 = null;
        double[] array10 = null;
        double result = 0;
        try {
            method = cls.getMethod("getSeqNumber", noparams);
            seqNumber = (int) method.invoke(payload, null);
            method = cls.getMethod("getPayloadSize", noparams);
            payloadSize = (int) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray1", noparams);
            array1 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray2", noparams);
            array2 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray3", noparams);
            array3 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray4", noparams);
            array4 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray5", noparams);
            array5 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray6", noparams);
            array6 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray7", noparams);
            array7 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray8", noparams);
            array8 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray9", noparams);
            array9 = (double[]) method.invoke(payload, null);
            method = cls.getDeclaredMethod("getArray10", noparams);
            array10 = (double[]) method.invoke(payload, null);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        if (array1 != null) {
            int len = array1.length;
            double avg1 = 0;
            double avg2 = 0;
            double avg3 = 0;
            double avg4 = 0;
            double avg5 = 0;
            double avg6 = 0;
            double avg7 = 0;
            double avg8 = 0;
            double avg9 = 0;
            double avg10 = 0;
            int items = 0;
            if (len < 10) {
                items = len;
            } else {
                items = 10;
            }
            for (int i = 0; i < items; i++) {
                avg1 += array1[i];
                avg2 += array2[i];
                avg3 += array3[i];
                avg4 += array4[i];
                avg5 += array5[i];
                avg6 += array6[i];
                avg7 += array7[i];
                avg8 += array8[i];
                avg9 += array9[i];
                avg10 += array10[i];
            }
            avg1 = avg1 / items;
            avg2 = avg2 / items;
            avg3 = avg3 / items;
            avg4 = avg4 / items;
            avg5 = avg5 / items;
            avg6 = avg6 / items;
            avg7 = avg7 / items;
            avg8 = avg8 / items;
            avg9 = avg9 / items;
            avg10 = avg10 / items;

            result = avg1 + avg2 + avg3 + avg4 + avg5 + avg6 + avg7 + avg8 + avg9 + avg10;
        }

        System.out.println("DeserializationDataPlaneRule: result: " + result + " dataType: " + dataType + ", seqNumber: " + seqNumber + ", payloadSize " + payloadSize + ", from " + up.getSourceNodeId());
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
            seqNumber = (int) method.invoke(payload, null);
            method = cls.getMethod("getPayloadSize", noparams);
            payloadSize = (int) method.invoke(payload, null);

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
