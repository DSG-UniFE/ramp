package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class GenericDataType extends AbstractDataType implements Serializable {
    private static final long serialVersionUID = 5906835222202954282L;

    public GenericDataType() {
        super();
    }

    public GenericDataType(int seqNumber, int payloadSize) {
        super(seqNumber, payloadSize);
    }
}
