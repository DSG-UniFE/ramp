package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager;

import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManager;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.forwardingListener.AdvancedDataPlaneForwardingListener;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class AdvancedDataPlaneRulesManager {
    /**
     * Data structure to keep track of the rules to apply given
     * a serialization ID. In this implementation we use
     * the serialVersionUID to identify the DataType
     */
    private Map<Long, List<String>> activeRules;

    /**
     * List that contains all the rules currently available for this
     * ControllerClient. At the beginning it will contain the default
     * rules while at runtime it could contain also rules sent from the
     * ControllerService.
     */
    private Map<String, String> rulesAvailableDatabase;

    private AdvancedDataPlaneForwardingListener forwardingListener;

    private DataTypesManager dataTypesManager;

    /**
     * advancedDataPlaneRulesManager instance.
     */
    private static AdvancedDataPlaneRulesManager advancedDataPlaneRulesManager = null;

    private AdvancedDataPlaneRulesManager() {
        activeRules = new ConcurrentHashMap<>();
        rulesAvailableDatabase = new ConcurrentHashMap<>();
        dataTypesManager = DataTypesManager.getInstance();
    }

    synchronized public static AdvancedDataPlaneRulesManager getInstance() {
        if (advancedDataPlaneRulesManager == null) {
            advancedDataPlaneRulesManager = new AdvancedDataPlaneRulesManager();
            advancedDataPlaneRulesManager.initialise();
        }

        System.out.println("AdvancedDataPlaneRulesManager STARTED");

        return advancedDataPlaneRulesManager;
    }

    private void initialise() {
        /*
         * Initialise default rules. Scan the rules currently available locally
         */
        String defaultRulesPackage = "it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultRules";
        List<Class<?>> rulesAvailable = GeneralUtils.getClassesInPackage(defaultRulesPackage);
        for (Class<?> aClass : rulesAvailable) {
            rulesAvailableDatabase.put(aClass.getSimpleName(), aClass.getName());
        }

        /*
         * TODO Handle the case in which there are already files received at runtime
         */

        forwardingListener = new AdvancedDataPlaneForwardingListener();
        Dispatcher.getInstance(false).addPacketForwardingListener(forwardingListener);
    }

    public Set<String> getRulesAvailable() {
        return rulesAvailableDatabase.keySet();
    }

    public boolean addDataTypeRule(long dataTypeId, String rule) {
        /*
         * First check if the dataType reference exists in the
         */
        if (dataTypesManager.containsDataTypeById(dataTypeId)) {
            if (!activeRules.containsKey(dataTypeId)) {
                List<String> rules = new ArrayList<>();
                rules.add(rule);
                activeRules.put(dataTypeId, rules);
            } else {
                activeRules.get(dataTypeId).add(rule);
            }

            return true;
        }
        return false;
    }

    public void removeDataTypeRule(long dataType, String rule) {
        if(activeRules.containsKey(dataType)) {
            activeRules.get(dataType).remove(rule);
        }
    }

    public Map<String, List<String>> getActiveRules() {
        Map<String, List<String>> humanReadableActiveRules = new ConcurrentHashMap<>();

        for(Long dataTypeId : activeRules.keySet()) {
            humanReadableActiveRules.put(dataTypesManager.getDataTypeName(dataTypeId), activeRules.get(dataTypeId));
        }

        return humanReadableActiveRules;
    }
}
