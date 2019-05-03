package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import java.util.Set;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * The methods declared here should be related only to information retrieval.
 */
public interface DataTypesManagerInterface {

    public Set<String> getAvailableDataTypes();

    public long getDataTypeId(String dataTypeName);

    public String getDataTypeName(long dataTypeId);

    public boolean containsDataType(String dataTypeName);

    public boolean containsDataType(long dataTypedId);

    public Class getDataTypeClassObject(String dataTypeName);

    public Class getDataTypeClassObjec(long dataTypeId);
}
