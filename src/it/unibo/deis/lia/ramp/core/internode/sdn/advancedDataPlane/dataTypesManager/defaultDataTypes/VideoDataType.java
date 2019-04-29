package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;

import java.io.Serializable;

public class VideoDataType extends AbstractDataType implements Serializable {
    private static final long serialVersionUID = -1293781459764390227L;

    public VideoDataType() {
        super();
    }

    public VideoDataType(int seqNumber, int payloadSize) {
        super(seqNumber, payloadSize);
    }
}
