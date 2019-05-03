package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public abstract class AbstractDataType implements Serializable {
    private static final long serialVersionUID = -6368488354603521877L;

    protected int seqNumber;

    protected byte[] payload;

    public AbstractDataType() {
        this.seqNumber = -1;
        this.payload = new byte[0];
    }

    public AbstractDataType(int seqNumber, int payloadSize) {
        this.seqNumber = seqNumber;
        this.payload = new byte[payloadSize];
    }

    public void setPayloadSize(int payloadSize) {
        this.payload = new byte[payloadSize];
    }

    public int getPayloadSize() {
        return this.payload.length;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }

    public int getSeqNumber() {
        return this.seqNumber;
    }
}
