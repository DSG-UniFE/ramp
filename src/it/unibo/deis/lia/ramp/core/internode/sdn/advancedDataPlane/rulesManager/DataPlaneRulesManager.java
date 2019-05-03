package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataPlaneMessage.DataPlaneMessage;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManager;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManagerInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.forwardingListener.DataPlaneForwardingListener;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import it.unibo.deis.lia.ramp.util.rampClassLoader.RampClassLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class DataPlaneRulesManager {
    /**
     * List that contains all the rules currently available for this
     * ControllerClient. At the beginning it will contain the default
     * rules while at runtime it could contain also rules sent from the
     * ControllerService.
     */
    private Map<String, String> dataPlaneRulesDatabase;

    /**
     * Data structure used to contains all the Class objects managed
     * by this manager.
     */
    private Map<String, Class> dataPlaneRulesClassMapping;

    /**
     * Data structure to keep track of the current active rules to apply given
     * a serialization ID. In this implementation we use
     * the serialVersionUID to identify the DataType.
     */
    private Map<Long, List<String>> activeDataPlaneRules;

    /**
     * This listener is in charge to capture the traversing packets and if defined
     * to apply rules to those packets.
     */
    private DataPlaneForwardingListener forwardingListener;

    /**
     * This String specifies the directory that the DataPlaneRulesManager
     * must use for its purposes.
     */
    private String dataPlaneRulesManagerDirectoryName = "./temp/dataPlaneRulesManager";

    /**
     * This String specifies the directory that the DataPlaneRulesManager
     * must use in order to load user defined DataPlaneRules sent by
     * the ControllerService at runtime.
     */
    private String userDefinedDataPlaneRulesDirectoryName = dataPlaneRulesManagerDirectoryName + "/userDefinedDataPlaneRules";

    /**
     * DataTypesManager instance.
     */
    private DataTypesManagerInterface dataTypesManager;

    /**
     * We use the rampClassLoader in order to load the .class at runtime
     */
    private RampClassLoader rampClassLoader;

    /**
     * DataPlaneRulesManager instance.
     */
    private static DataPlaneRulesManager dataPlaneRulesManager = null;

    private DataPlaneRulesManager() {
        dataPlaneRulesDatabase = new ConcurrentHashMap<>();
        dataPlaneRulesClassMapping = new ConcurrentHashMap<>();
        activeDataPlaneRules = new ConcurrentHashMap<>();
        dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));

        File dataPlaneRuleManagerDirectoryFile = new File(dataPlaneRulesManagerDirectoryName);
        if (!dataPlaneRuleManagerDirectoryFile.exists()) {
            dataPlaneRuleManagerDirectoryFile.mkdir();
        }

        dataPlaneRuleManagerDirectoryFile = new File(userDefinedDataPlaneRulesDirectoryName);
        if (!dataPlaneRuleManagerDirectoryFile.exists()) {
            dataPlaneRuleManagerDirectoryFile.mkdir();
        }

        rampClassLoader = RampEntryPoint.getRampClassLoader();
    }

    synchronized public static DataPlaneRulesManager getInstance() {
        if (dataPlaneRulesManager == null) {
            dataPlaneRulesManager = new DataPlaneRulesManager();
            dataPlaneRulesManager.initialise();
        }

        System.out.println("DataPlaneRulesManager STARTED");

        return dataPlaneRulesManager;
    }

    private void initialise() {
        /*
         * Add the dataPlaneRulesManager managed directory to classpath so that
         * the user defined DataPlaneRules located in this directory can be
         * founded at runtime.
         */
        rampClassLoader.addPath(userDefinedDataPlaneRulesDirectoryName);

        /*
         * Initialise default DataPlaneRules. Scan the rules currently available locally
         */
        String defaultRulesPackage = "it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules";
        List<Class<?>> defaultDataPlaneRuleClasses = GeneralUtils.getClassesInPackage(defaultRulesPackage);
        for (Class<?> dataPlaneRuleClass : defaultDataPlaneRuleClasses) {
            addDataPlaneRuleToDataBase(dataPlaneRuleClass);
        }

        /*
         * Initialise user defined DataPlaneRules if available,
         * the ones currently available from previous ControllerClient sessions.
         */
        Set<String> userDefinedDataPlaneRules = Stream.of(new File(userDefinedDataPlaneRulesDirectoryName).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());

        for (String dataPlaneRuleFileName : userDefinedDataPlaneRules) {
            String dataPlaneRuleClassName = dataPlaneRuleFileName.replaceFirst("[.][^.]+$", "");
            try {
                Class cls = rampClassLoader.loadClass(dataPlaneRuleClassName);
                addDataPlaneRuleToDataBase(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        /*
         * Initialise the dataPlaneForwardingListener
         */
        forwardingListener = new DataPlaneForwardingListener();
        Dispatcher.getInstance(false).addPacketForwardingListener(forwardingListener);
    }

    public void deactivate() {
        /*
         * Not used at the moment because in the case there is a ControllerClient in the same
         * machine of the ControllerService, if the ControllerService stops this manager
         * will become null for the ControllerClient.
         */

        /*
         * TODO The best thing to do is before this manager
         * TODO is deactivated the ControllerService should send a message
         * TODO to all ControllerClients in order to remove all the rules
         * TODO currently active.
         */
        if(dataPlaneRulesManager != null) {
            Dispatcher.getInstance(false).removePacketForwardingListener(forwardingListener);
            forwardingListener = null;
            dataPlaneRulesDatabase = null;
            dataPlaneRulesClassMapping = null;
            activeDataPlaneRules = null;
            dataTypesManager = null;
            rampClassLoader = null;

            System.out.println("DataPlaneRulesManager STOP");
        }
    }

    private void addDataPlaneRuleToDataBase(Class dataPlaneRuleClass) {
        dataPlaneRulesDatabase.put(dataPlaneRuleClass.getSimpleName(), dataPlaneRuleClass.getName());
        dataPlaneRulesClassMapping.put(dataPlaneRuleClass.getSimpleName(), dataPlaneRuleClass);
    }

    private void removeDataPlaneRuleFromDataBase(String dataPlaneRuleName) {
        /*
         * Clean user defined DataPlaneRule references from the database.
         */
        dataPlaneRulesDatabase.remove(dataPlaneRuleName);
        dataPlaneRulesClassMapping.remove(dataPlaneRuleName);
        /*
         * Delete stored .class file
         */
        String dataPlaneRuleFileName = dataPlaneRuleName + ".class";
        File dataTypeClassFile = new File(userDefinedDataPlaneRulesDirectoryName + "/" + dataPlaneRuleFileName);

        if (dataTypeClassFile.delete()) {
            System.out.println("DataPlaneRulesManager: " + dataPlaneRuleFileName + " successfully deleted.");
        } else {
            System.out.println("DataPlaneRulesManager: " + dataPlaneRuleFileName + " not deleted.");
        }
    }

    public Set<String> getAvailableDataPlaneRules() {
        return dataPlaneRulesDatabase.keySet();
    }

    /**
     * Method to add at runtime a new DataPlaneRule sent by the ControllerService
     *
     * @param dataPlaneMessage sent by the ControllerService
     * @return boolean
     */
    public boolean addUserDefinedDataPlaneRule(DataPlaneMessage dataPlaneMessage) {
        String dataPlaneRuleFileName = dataPlaneMessage.getFileName();
        String dataPlaneRuleClassName = dataPlaneMessage.getClassName();

        if (containsDataPlaneRule(dataPlaneRuleClassName)) {
            System.out.println("DataPlaneRulesManager: user defined DataPlaneRule: " + dataPlaneRuleClassName + "already exists.");
            return true;
        }

        File dataPlaneRuleClassFile = new File(userDefinedDataPlaneRulesDirectoryName + "/" + dataPlaneRuleFileName);
        byte[] bytes = dataPlaneMessage.getClassFile();

        try {
            OutputStream os = new FileOutputStream(dataPlaneRuleClassFile);
            os.write(bytes);
            os.close();
            System.out.println("DataPlaneRulesManager: user defined DataPlaneRule: " + dataPlaneRuleFileName + "successfully stored.");
        } catch (Exception e) {
            System.out.println("DataPlaneRulesManager: user defined DataPlaneRule: " + dataPlaneRuleFileName + "received but not stored.");
            return false;
        }

        try {
            Class dataPlaneRuleClass = rampClassLoader.loadClass(dataPlaneRuleClassName);
            addDataPlaneRuleToDataBase(dataPlaneRuleClass);
        } catch (ClassNotFoundException e) {
            System.out.println("DataPlaneRulesManager: user defined DataPlaneRule: " + dataPlaneRuleClassName + "not loaded by RampClassLoader.");
            return false;
        }
        return true;
    }

    public void removeUserDefinedDataPlaneRule(String dataPlaneRuleSimpleClassName) {
        removeDataPlaneRuleFromDataBase(dataPlaneRuleSimpleClassName);
    }

    public void executeUnicastHeaderDataPlaneRule(long dataTypeId, UnicastHeader uh) {
        String dataTypeName = dataTypesManager.getDataTypeName(dataTypeId);
        List<String> activeDataTypeRules = activeDataPlaneRules.get(dataTypeId);
        Object dataPlaneRule = null;
        Method method;
        for (String dataPlaneRuleName : activeDataTypeRules) {
            Class dataPlaneRuleClass = dataPlaneRulesClassMapping.get(dataPlaneRuleName);
            try {
                dataPlaneRule = dataPlaneRuleClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            try {
                method = dataPlaneRuleClass.getDeclaredMethod("applyRuleToUnicastHeader", UnicastHeader.class);
                method.invoke(dataPlaneRule, uh);
            } catch (NoSuchMethodException e) {
                /*
                 * This means that the empty method of AbstractDataPlaneRule has not been overriden.
                 */
                continue;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            System.out.println("DataPlaneRulesManager: unicastHeaderDataPlaneRule: " + dataPlaneRuleName + " for DataType: " + dataTypeName + " successfully applied.");
        }
    }

    public void executeUnicastPacketDataPlaneRule(long dataTypeId, UnicastPacket up) {
        String dataTypeName = dataTypesManager.getDataTypeName(dataTypeId);
        List<String> activeDataTypeRules = activeDataPlaneRules.get(dataTypeId);
        Object dataPlaneRule = null;
        Method method;
        for (String dataPlaneRuleName : activeDataTypeRules) {
            Class dataPlaneRuleClass = dataPlaneRulesClassMapping.get(dataPlaneRuleName);
            try {
                dataPlaneRule = dataPlaneRuleClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            try {
                method = dataPlaneRuleClass.getDeclaredMethod("applyRuleToUnicastPacket", UnicastPacket.class);
                method.invoke(dataPlaneRule, up);
            } catch (NoSuchMethodException e) {
                /*
                 * This means that the empty method of AbstractDataPlaneRule has not been overriden.
                 */
                continue;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            System.out.println("DataPlaneRulesManager: unicastPacketDataPlaneRule: " + dataPlaneRuleName + " for DataType: " + dataTypeName + " successfully applied.");
        }
    }


    public void executeBroadcastPacketDataPlaneRule(long dataTypeId, BroadcastPacket bp) {
        String dataTypeName = dataTypesManager.getDataTypeName(dataTypeId);
        List<String> activeDataTypeRules = activeDataPlaneRules.get(dataTypeId);
        Object dataPlaneRule = null;
        Method method;
        for (String dataPlaneRuleName : activeDataTypeRules) {
            Class dataPlaneRuleClass = dataPlaneRulesClassMapping.get(dataPlaneRuleName);
            try {
                dataPlaneRule = dataPlaneRuleClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            try {
                method = dataPlaneRuleClass.getDeclaredMethod("applyRuleToBroadcastPacket", BroadcastPacket.class);
                method.invoke(dataPlaneRule, bp);
            } catch (NoSuchMethodException e) {
                /*
                 * This means that the empty method of AbstractDataPlaneRule has not been overriden.
                 */
                continue;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            System.out.println("DataPlaneRulesManager: broadcastPacketDataPlaneRule: " + dataPlaneRuleName + " for DataType: " + dataTypeName + " successfully applied.");
        }
    }

    public boolean addDataPlaneRule(long dataTypeId, String dataPlaneRuleName) {
        /*
         * First check if the dataType reference exists in the
         * DataTypesManager.
         */
        if (dataTypesManager.containsDataType(dataTypeId)) {
            if (!activeDataPlaneRules.containsKey(dataTypeId)) {
                List<String> rules = new ArrayList<>();
                rules.add(dataPlaneRuleName);
                activeDataPlaneRules.put(dataTypeId, rules);
            } else {
                activeDataPlaneRules.get(dataTypeId).add(dataPlaneRuleName);
            }

            return true;
        }
        return false;
    }

    public void removeDataPlaneRule(String dataTypeName, String dataPlaneRuleName) {
        removeDataPlaneRule(dataTypesManager.getDataTypeId(dataTypeName), dataPlaneRuleName);
    }

    public void removeDataPlaneRule(long dataTypeId, String dataPlaneRuleName) {
        if (activeDataPlaneRules.containsKey(dataTypeId)) {
            activeDataPlaneRules.get(dataTypeId).remove(dataPlaneRuleName);
        }
    }

    public boolean containsDataPlaneRuleForDataType(String dataTypeName) {
        return containsDataPlaneRuleForDataType(dataTypesManager.getDataTypeId(dataTypeName));
    }

    public boolean containsDataPlaneRuleForDataType(long dataTypeId) {
        return activeDataPlaneRules.containsKey(dataTypeId);
    }

    public boolean containsDataPlaneRule(String dataPlaneRuleSimpleClassName) {
        return this.dataPlaneRulesDatabase.containsKey(dataPlaneRuleSimpleClassName);
    }

    public Class getDataPlaneRuleClassObjectByName(String dataPlaneRuleSimpleClassName) {
        return this.dataPlaneRulesClassMapping.get(dataPlaneRuleSimpleClassName);
    }

    public Map<String, List<String>> getActiveDataPlaneRulesByDataType() {
        Map<String, List<String>> activeRulesByDataType = new ConcurrentHashMap<>();

        for (Long dataTypeId : activeDataPlaneRules.keySet()) {
            activeRulesByDataType.put(dataTypesManager.getDataTypeName(dataTypeId), activeDataPlaneRules.get(dataTypeId));
        }

        return activeRulesByDataType;
    }
}
