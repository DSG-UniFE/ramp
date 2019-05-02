package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import java.util.Set;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * The methods declared here should be related only to information retrieval.
 */
public interface DataTypesManagerInterface {

    public Set<String> getAvailableDataTypes();

    public long getDataTypeId(String dataTypeSimpleClassName);

    public String getDataTypeName(long dataTypeId);

    public boolean containsDataTypeByName(String dataTypeSimpleClassName);

    public Class getDataTypeClassObjectByName(String dataTypeSimpleClassName);

    public Class getDataTypeClassObjectById(long dataTypeId);
}
