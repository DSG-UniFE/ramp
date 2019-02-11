package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class SDNControllerMessage implements Serializable {

    private static final long serialVersionUID = -1361610314097603574L;

    private int seqNumber;

    private byte[] payload;

    public SDNControllerMessage(int seqNumber, int payloadSize) {
        this.seqNumber = seqNumber;
        this.payload = new byte[payloadSize];
    }

    public int getPayloadSize() {
        return this.payload.length;
    }

    public int getSeqNumber() {
        return this.seqNumber;
    }
}
