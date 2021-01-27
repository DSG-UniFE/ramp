package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;

import java.io.Serializable;

/**
 * @author Matteo Mendula
 */
public class VideoDataType extends AbstractDataType implements Serializable {

    private static final long serialVersionUID = 7332943357871452826L;
    private int value;

    public VideoDataType() {
        super();
        this.value = -1;
    }

    public VideoDataType(int seqNumber, int payloadSize, int value) {
        super(seqNumber, payloadSize);
        this.value = value;
    }

//    ----------------------- GETTER


    public long getValue() {
        return this.value;
    }

//    ----------------------- SETTER

    public void setValue(int value) {
        this.value = value;
    }

    public int getSeqNumber() {
        return super.getSeqNumber();
    }
    public boolean getIsDroppable() {
        return super.getIsDroppable();
    }
    public long getDelayable() {
        return super.getDelayable();
    }

}
