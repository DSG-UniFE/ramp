package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class SDNControllerMessage implements Serializable {

    private static final long serialVersionUID = -1361610314097603574L;

    private int seqNumber;

    private int testRepetition;

    private byte[] payload;

    public SDNControllerMessage() {
        this.seqNumber = -1;
        this.payload = new byte[0];
    }

    public SDNControllerMessage(int seqNumber, int payloadSize) {
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

    public void setTestRepetition(int testRepetition) {
        this.testRepetition = testRepetition;
    }

    public int getTestRepetition() {
        return this.testRepetition;
    }
}
