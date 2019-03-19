package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerService.ControllerService;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import org.graphstream.ui.swingViewer.DefaultView;

import java.util.Iterator;

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

    public Iterator<Integer> getActiveClients() {
        return controllerService.getActiveClients();
    }

    public TrafficEngineeringPolicy getFlowPolicy() {
        return controllerService.getTrafficEngineeringPolicy();
    }

    public void updateFlowPolicy(TrafficEngineeringPolicy trafficEngineeringPolicy) {
        controllerService.updateFlowPolicy(trafficEngineeringPolicy);
    }

    public DefaultView getGraph() {
        return controllerService.getGraph();
    }

    private static void sleep(int sleepFor) {
        try {
            Thread.sleep(sleepFor * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
