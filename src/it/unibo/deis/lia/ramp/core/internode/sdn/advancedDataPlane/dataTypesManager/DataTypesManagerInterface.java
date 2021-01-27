package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import java.util.Set;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * The methods declared here should be related only to information retrieval.
 *
 * The class that implements these methods should be retrieved
 * using the ComponentLocator object.
 *
 * Example of usage:
 * DataTypesManagerInterface dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));
 */
public interface DataTypesManagerInterface {

    Set<String> getAvailableDataTypes();

    long getDataTypeId(String dataTypeName);

    String getDataTypeName(long dataTypeId);

    boolean containsDataType(String dataTypeName);

    boolean containsDataType(long dataTypedId);

    Class getDataTypeClassObject(String dataTypeName);

    Class getDataTypeClassObject(long dataTypeId);
}
