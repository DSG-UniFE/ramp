package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerService.ControllerService;
import it.unibo.deis.lia.ramp.core.internode.sdn.routingPolicy.RoutingPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import org.graphstream.ui.swingViewer.DefaultView;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class SDNControllerService {

    private static SDNControllerService SDNControllerService = null;
    private static ControllerService controllerService = null;
    private static SDNControllerServiceJFrame csjf;

    public static synchronized SDNControllerService getInstance() {
        if (SDNControllerService == null) {
            SDNControllerService = new SDNControllerService();
        }
        csjf.setVisible(true);
        return SDNControllerService;
    }

    private SDNControllerService() {
        System.out.println("SDNControllerService START");
        controllerService = ControllerService.getInstance();
        sleep(2);
        csjf = new SDNControllerServiceJFrame(this);
    }

    public boolean isActive() { return SDNControllerService != null; }

    public void stopService() {
        System.out.println("SDNControllerService STOP");
        try {
            controllerService.stopService();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        controllerService = null;
        SDNControllerService = null;
        System.out.println("SDNControllerService FINISHED");
    }

    public Set<Integer> getActiveClients() {
        return controllerService.getActiveClients();
    }

    public TrafficEngineeringPolicy getTrafficEngineeringPolicy() {
        return controllerService.getTrafficEngineeringPolicy();
    }

    public void updateTrafficEngineeringPolicy(TrafficEngineeringPolicy trafficEngineeringPolicy) {
        controllerService.updateTrafficEngineeringPolicy(trafficEngineeringPolicy);
    }

    public RoutingPolicy getRoutingPolicy() {
        return controllerService.getRoutingPolicy();
    }

    public void updateRoutingPolicy(RoutingPolicy routingPolicy) {
        controllerService.updateRoutingPolicy(routingPolicy);
    }

    public DefaultView getGraph() {
        return controllerService.getGraph();
    }

    public Set<String> getAvailableDataTypes() {
        return controllerService.getAvailableDataTypes();
    }

    public Set<String> getAvailableDataPlaneRules() {
        return controllerService.getAvailableDataPlaneRules();
    }

    public Map<String, List<String>> getActiveDataPlaneRules() {
        return controllerService.getActiveDataPlaneRules();
    }

    public boolean addUserDefinedDataType(String dataTypeFileName, File dataTypeFile) {
        return controllerService.addUserDefinedDataType(dataTypeFileName, dataTypeFile);
    }

    public boolean addUserDefinedDataPlaneRule(String dataPlaneRuleFileName, File dataPlaneRuleFile) {
        return controllerService.addUserDefinedDataPlaneRule(dataPlaneRuleFileName, dataPlaneRuleFile);
    }

    public boolean addDataPlaneRule(String dataType, String dataPlaneRule) {
        return controllerService.addDataPlaneRule(dataType, dataPlaneRule);
    }

    public boolean addDataPlaneRule(String dataType, String dataPlaneRule, List<Integer> clientsNodeToNotify) {
        return controllerService.addDataPlaneRule(dataType, dataPlaneRule, clientsNodeToNotify);
    }

    public void removeDataPlaneRule(String dataType, String dataPlaneRule) {
        controllerService.removeDataPlaneRule(dataType, dataPlaneRule);
    }

    public void removeDataPlaneRule(String dataType, String dataPlaneRule, List<Integer> clientsNodeToNotify) {
        controllerService.removeDataPlaneRule(dataType, dataPlaneRule, clientsNodeToNotify);
    }

    private static void sleep(int sleepFor) {
        try {
            Thread.sleep(sleepFor * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
