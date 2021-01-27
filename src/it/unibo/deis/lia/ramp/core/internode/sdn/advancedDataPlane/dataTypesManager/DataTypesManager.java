package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataPlaneMessage.DataPlaneMessage;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.util.rampClassLoader.RampClassLoader;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.file.Files;

/**
 * @author Dmitrij David Padalino Montenero
 * Manager that handles all the DataType used in case of advanced data type feature usage.
 */
public class DataTypesManager implements DataTypesManagerInterface {

    /**
     * Data structure used to store the mapping between the
     * DataType and its SerialVersionUID that in this implementation
     * is treated like the global unique ID to identify the DataType.
     */
    private Map<String, Long> dataTypesMappingByName;

    /**
     * Data structure used to store the mapping between the
     * the SerialVersionUID and the DataType name.
     */
    private Map<Long, String> dataTypesMappingById;

    /**
     * Data structure used to contains all the Class objects managed
     * by this manager.
     */
    private Map<String, Class> dataTypeClassMapping;

    /**
     * This String specifies the directory that the DataTypeManger
     * must use for its purposes.
     */
    private String dataTypeManagerDirectoryName = "./temp/dataTypeManager";

    /**
     * This String specifies the directory that the DataTypeManger must use
     * in order to store and load user defined DataTypes sent by the
     * ControllerService at runtime.
     */
    private String userDefinedDataTypesDirectoryName = dataTypeManagerDirectoryName + "/userDefinedDataTypes";

    /**
     * We use the rampClassLoader in order to load the .class files at runtime.
     */
    private RampClassLoader rampClassLoader;

    /**
     * DataTypesManager instance.
     */
    private static DataTypesManager dataTypesManager;

    private DataTypesManager() {
        dataTypesMappingByName = new ConcurrentHashMap<>();
        dataTypesMappingById = new ConcurrentHashMap<>();
        dataTypeClassMapping = new ConcurrentHashMap<>();

        File dataTypeManagerDirectoryFile = new File(dataTypeManagerDirectoryName);
        if (!dataTypeManagerDirectoryFile.exists()) {
            dataTypeManagerDirectoryFile.mkdir();
        }

        dataTypeManagerDirectoryFile = new File(userDefinedDataTypesDirectoryName);
        if (!dataTypeManagerDirectoryFile.exists()) {
            dataTypeManagerDirectoryFile.mkdir();
        }

        rampClassLoader = RampEntryPoint.getRampClassLoader();
    }

    synchronized public static DataTypesManager getInstance() {
        if (dataTypesManager == null) {
            dataTypesManager = new DataTypesManager();
            dataTypesManager.initialise();
        }

        System.out.println("DataTypesManager STARTED");

        return dataTypesManager;
    }

    private void initialise() {
        /*
         * Add the dataTypesManager manged directory to classpath so that
         * the user defined DataTypes located in this directory can be
         * founded at runtime.
         */
        rampClassLoader.addPath(userDefinedDataTypesDirectoryName);

        /*
         * Initialise default DataTypes, the ones currently available at development time.
         */
        String defaultDataTypesPackage = "it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes";

        List<Class<?>> defaultDataTypeClasses = GeneralUtils.getClassesInPackage(defaultDataTypesPackage);

        for (Class<?> dataTypeClass : defaultDataTypeClasses) {
            addDataTypeToDataBase(dataTypeClass);
        }

        /*
         * Initialise user defined DataTypes if available,
         * the ones currently available from previous ControllerClient sessions.
         */
        Set<String> userDefinedDataTypes = new HashSet<>();
        if (Files.exists(Paths.get(userDefinedDataTypesDirectoryName))){
            System.out.println("userDefinedDataTypesDirectoryName ("+userDefinedDataTypesDirectoryName+") does not exist");
            userDefinedDataTypes = Stream.of(new File(userDefinedDataTypesDirectoryName).listFiles())
                    .filter(file -> !file.isDirectory())
                    .map(File::getName)
                    .collect(Collectors.toSet());
        }

        for (String dataTypeFileName : userDefinedDataTypes) {
            String dataTypeClassName = dataTypeFileName.replaceFirst("[droppingPacketsEnabled.][^.]+$", "");
            try {
                Class cls = rampClassLoader.loadClass(dataTypeClassName);
                addDataTypeToDataBase(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void deactivate() {
        /*
         * Not used at the moment because in the case there is a ControllerClient in the same
         * machine of the ControllerService, if the ControllerService stops this manager
         * will become null for the ControllerClient.
         */
        if(dataTypesManager != null) {
            dataTypesMappingByName = null;
            dataTypesMappingById = null;
            dataTypeClassMapping = null;
            rampClassLoader = null;
            dataTypesManager = null;
        }
    }

    private void addDataTypeToDataBase(Class dataTypeClass) {
        dataTypesMappingByName.put(dataTypeClass.getSimpleName(), ObjectStreamClass.lookup(dataTypeClass).getSerialVersionUID());
        dataTypesMappingById.put(ObjectStreamClass.lookup(dataTypeClass).getSerialVersionUID(), dataTypeClass.getSimpleName());
        dataTypeClassMapping.put(dataTypeClass.getSimpleName(), dataTypeClass);
    }

    private void removeDataTypeFromDataBase(String dataTypeName) {
        long dataTypeId = dataTypesMappingByName.get(dataTypeName);
        /*
         * Clean user defined DataType references from the database.
         */
        dataTypesMappingByName.remove(dataTypeName);
        dataTypesMappingById.remove(dataTypeId);
        dataTypeClassMapping.remove(dataTypeName);
        /*
         * Delete stored .class file
         */
        String dataTypeFileName = dataTypeName + ".class";
        File dataTypeClassFile = new File(userDefinedDataTypesDirectoryName + "/" + dataTypeFileName);

        if (dataTypeClassFile.delete()) {
            System.out.println("DataTypesManager: " + dataTypeFileName + " successfully deleted.");
        } else {
            System.out.println("DataTypesManager: " + dataTypeFileName + " not deleted.");
        }
    }

    public Set<String> getAvailableDataTypes() {
        return dataTypesMappingByName.keySet();
    }

    /**
     * Method to add at runtime a new DataType sent by the ControllerService
     *
     * @param dataPlaneMessage sent by the ControllerService
     * @return boolean
     */
    public boolean addUserDefinedDataType(DataPlaneMessage dataPlaneMessage) {
        String dataTypeFileName = dataPlaneMessage.getFileName();
        String dataTypeClassName = dataPlaneMessage.getClassName();

        if (containsDataType(dataTypeClassName)) {
            System.out.println("DataTypeManager: user defined DataType: " + dataTypeClassName + "already exists.");
            return true;
        }

        File dataTypeClassFile = new File(userDefinedDataTypesDirectoryName + "/" + dataTypeFileName);
        byte[] bytes = dataPlaneMessage.getClassFile();

        try {
            OutputStream os = new FileOutputStream(dataTypeClassFile);
            os.write(bytes);
            os.close();
            System.out.println("DataTypeManager: user defined DataType: " + dataTypeFileName + "successfully stored.");
        } catch (Exception e) {
            System.out.println("DataTypeManager: user defined DataType: " + dataTypeFileName + "received but not stored.");
            return false;
        }

        try {
            Class dataTypeClass = rampClassLoader.loadClass(dataTypeClassName);
            addDataTypeToDataBase(dataTypeClass);
        } catch (ClassNotFoundException e) {
            System.out.println("DataTypeManager: user defined DataType: " + dataTypeClassName + "not loaded by RampClassLoader.");
            return false;
        }
        return true;
    }

    public void removeUserDefinedDataType(String dataTypeSimpleClassName) {
        removeDataTypeFromDataBase(dataTypeSimpleClassName);
    }

    public long getDataTypeId(String dataTypeName) {
        return dataTypesMappingByName.get(dataTypeName);
    }

    public String getDataTypeName(long dataTypeId) {
        return dataTypesMappingById.get(dataTypeId);
    }

    public boolean containsDataType(long dataTypeId) {
        return this.dataTypesMappingById.containsKey(dataTypeId);
    }

    public boolean containsDataType(String dataTypeName) {
        return this.dataTypesMappingByName.containsKey(dataTypeName);
    }

    /**
     * Method to use in place of Class.forName for DataType Class object retrieval.
     *
     * @param dataTypeName simple name of the DataType class
     * @return the Class object of the provided dataType
     */
    public Class getDataTypeClassObject(String dataTypeName) {
        return this.dataTypeClassMapping.get(dataTypeName);
    }

    /**
     * Method to use in place of Class.forName for DataType Class object retrieval.
     *
     * @param dataTypeId serialVersionUID of the DataType class
     * @return the Class object of the provided dataTypeId
     */
    public Class getDataTypeClassObject(long dataTypeId) {
        return getDataTypeClassObject(getDataTypeName(dataTypeId));
    }
}
