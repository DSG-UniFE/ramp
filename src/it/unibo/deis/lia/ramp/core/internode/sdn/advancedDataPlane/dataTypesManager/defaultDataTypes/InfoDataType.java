package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;

import java.io.Serializable;

public class InfoDataType extends AbstractDataType implements Serializable {

    private static final long serialVersionUID = -1631743108532280182L;

    private int value;

    public InfoDataType() {
        super();
        this.value = -1;
    }

    public InfoDataType(int seqNumber, int payloadSize, int value) {
        super(seqNumber, payloadSize);
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
