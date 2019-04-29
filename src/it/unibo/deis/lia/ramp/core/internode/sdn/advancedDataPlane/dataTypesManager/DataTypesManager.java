package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.dataTypeMessage.DataTypeMessage;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class DataTypesManager {

    private Map<String, Long> dataTypesMappingByName;

    private Map<Long, String> dataTypesMappingById;

    private Map<String, String> dataTypesDatabase;

    private Map<String, Class> dataTypeClassMapping;

    private static DataTypesManager dataTypesManager;

    /**
     * This String specifies the directory that the DataTypeManger must use in order to load classes from files sent
     * by the ControllerService.
     */
    private String dataTypeManagerDirectoryName = "./temp/dataTypeManager";

    private String dataTypeManagerDirectoryNameAbsolutePath;

    private ClassLoader userDefinedDataTypesClassLoader;

    private DataTypesManager() {
        dataTypesMappingByName = new ConcurrentHashMap<>();
        dataTypesMappingById = new ConcurrentHashMap<>();
        dataTypesDatabase = new ConcurrentHashMap<>();
        dataTypeClassMapping = new ConcurrentHashMap<>();

        File dataTypeManagerDirectoryFile = new File(dataTypeManagerDirectoryName);
        if (!dataTypeManagerDirectoryFile.exists()) {
            dataTypeManagerDirectoryFile.mkdir();
        }

        URL url = null;
        try {
            url = dataTypeManagerDirectoryFile.toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        URL[] urls = new URL[]{url};

        userDefinedDataTypesClassLoader = new URLClassLoader(urls);
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
         * Add the dataTypesManager handled directory to classpath so that
         * the user defined DataTypes located in this directory can be
         * founded at runtime.
         */
        try {
            GeneralUtils.addPathToClasspath(dataTypeManagerDirectoryName);

        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Initialise default data types, the ones currently available at development time.
         */
        String defaultDataTypesPackage = "it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes";
        List<Class<?>> rulesAvailable = GeneralUtils.getClassesInPackage(defaultDataTypesPackage);
        for (int i = 0; i < rulesAvailable.size(); i++) {
            Class<?> currentClass = rulesAvailable.get(i);
            addDataTypeToDataBase(currentClass);
        }

        /*
         * Initialise user defined data types if available,
         * the ones currently available from previous ControllerClient sessions.
         */
        Set<String> userDefinedDataTypes = Stream.of(new File(dataTypeManagerDirectoryName).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());

        if (userDefinedDataTypes.size() > 0) {
            for(String dataTypeFileName : userDefinedDataTypes) {
                String dataTypeClassName = dataTypeFileName.replaceFirst("[.][^.]+$", "");
                try {
                    Class cls = userDefinedDataTypesClassLoader.loadClass(dataTypeClassName);
                    addDataTypeToDataBase(cls);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void addDataTypeToDataBase(Class dataTypeClass) {
        dataTypesMappingByName.put(dataTypeClass.getSimpleName(), ObjectStreamClass.lookup(dataTypeClass).getSerialVersionUID());
        dataTypesMappingById.put(ObjectStreamClass.lookup(dataTypeClass).getSerialVersionUID(), dataTypeClass.getSimpleName());
        dataTypesDatabase.put(dataTypeClass.getSimpleName(), dataTypeClass.getName());
        dataTypeClassMapping.put(dataTypeClass.getSimpleName(), dataTypeClass);
    }

    public Set<String> getDataTypesAvailable() {
        return dataTypesDatabase.keySet();
    }

    public boolean addNewDataType(DataTypeMessage dataTypeMessage) {
        String dataTypeFileName = dataTypeMessage.getFileName();
        String dataTypeClassName = dataTypeMessage.getClassName();

        if (dataTypesDatabase.containsKey(dataTypeClassName)) {
            System.out.println("DataType Manager: user defined DataType: " + dataTypeClassName + "already exists.");
            return true;
        }

        File dataTypeFile = new File(dataTypeManagerDirectoryName + "/" + dataTypeFileName);
        byte[] bytes = dataTypeMessage.getFile();

        try {
            OutputStream os = new FileOutputStream(dataTypeFile);
            os.write(bytes);
            os.close();

            System.out.println("DataType Manager: user defined DataType: " + dataTypeFileName + "successfully stored.");
        } catch (Exception e) {
            System.out.println("DataType Manager: user defined DataType: " + dataTypeFileName + "received but not stored.");
            return false;
        }

        try {
            Class cls = userDefinedDataTypesClassLoader.loadClass(dataTypeClassName);
            addDataTypeToDataBase(cls);
        } catch (ClassNotFoundException e) {
            System.out.println("DataType Manager: user defined DataType: " + dataTypeClassName + "not loaded by URLClassLoader.");
            return false;
        }
        return true;
    }

    public long getDataTypeId(String className) {
        return dataTypesMappingByName.get(className);
    }

    public String getDataTypeName(long dataTypeId) {
        return dataTypesMappingById.get(dataTypeId);
    }

    public boolean containsDataTypeById(long id) {
        return this.dataTypesMappingById.containsKey(id);
    }

    public boolean containsDataTypeByName(String dataType) {
        return this.dataTypesMappingByName.containsKey(dataType);
    }

    public String getDataTypeClassName(String dataType) {
        return this.dataTypesDatabase.get(dataType);
    }

    public Class getClassForDataTypeName(String dataType) {
        return this.dataTypeClassMapping.get(dataType);
    }

    public Class getClassForDataTypeId(long dataTypeId) {
        return getClassForDataTypeName(getDataTypeName(dataTypeId));
    }

//    public Object createDataTypeObjectFromClassName(String className) {
//        Object dataTypeObject = null;
//        try {
//            dataTypeObject = Class.forName(dataTypesDatabase.get(className)).newInstance();
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InstantiationException e) {
//            e.printStackTrace();
//        }
//
//        return dataTypeObject;
//    }
//
//    public Object createDataTypeObjectFromSerialVersionUID(long serialVersionUID) {
//        return createDataTypeObjectFromClassName(dataTypesMapping.get(serialVersionUID));
//    }
}
