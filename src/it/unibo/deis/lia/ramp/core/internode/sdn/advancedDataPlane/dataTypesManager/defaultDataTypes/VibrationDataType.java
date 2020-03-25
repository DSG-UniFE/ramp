package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;


import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class VibrationDataType extends AbstractDataType implements Serializable {
    private static final long serialVersionUID = 793107902207408161L;

    private int vibrationValue;

    private long sentTimestamp;

    public VibrationDataType() {
        super();
        this.vibrationValue = -1;
        this.sentTimestamp = -1;
    }

    public VibrationDataType(int seqNumber, int payloadSize, int vibrationValue, long sentTimestamp) {
        super(seqNumber, payloadSize);
        this.vibrationValue = vibrationValue;
        this.sentTimestamp = sentTimestamp;
    }

    public int getVibrationValue() {
        return vibrationValue;
    }

    public void setVibrationValue(int vibrationValue) {
        this.vibrationValue = vibrationValue;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }

    public void setSentTimestamp(long sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }
}
