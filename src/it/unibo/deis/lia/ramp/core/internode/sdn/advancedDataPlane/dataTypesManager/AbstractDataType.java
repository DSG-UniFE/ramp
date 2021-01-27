package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public abstract class AbstractDataType implements Serializable {
    private static final long serialVersionUID = -6368488354603521877L;

    protected int seqNumber;
    private boolean isDroppable;
    private long delayable;
    private long sentTimestamp;
    protected byte[] payload;


    public AbstractDataType() {
        this.seqNumber = -1;
        this.isDroppable = false;
        this.delayable = 0;
        this.sentTimestamp = -1;
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

    public void setDroppable(boolean droppable) {
        isDroppable = droppable;
    }

    public void setDelayable(long delayable) {
        this.delayable = delayable;
    }

    public boolean getIsDroppable() {
        return isDroppable;
    }

    public long getDelayable() {
        return delayable;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }

    public void setSentTimestamp(long sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }
}
