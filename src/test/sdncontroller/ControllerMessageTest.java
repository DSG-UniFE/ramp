package test.sdncontroller;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageTest implements Serializable {
    private static final long serialVersionUID = -245784919612575364L;

    private int seqNumber;

    private int priority;

    private byte[] payload;

    public ControllerMessageTest() {
        this.seqNumber = -1;
        this.payload = new byte[0];
        this.priority = -1;
    }

    public ControllerMessageTest(int seqNumber, int payloadSize, int priority) {
        this.seqNumber = seqNumber;
        this.payload = new byte[payloadSize];
        this.priority = priority;
    }


    public int getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
