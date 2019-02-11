package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.internode.ControllerService;
import it.unibo.deis.lia.ramp.core.internode.FlowPolicy;
import org.graphstream.ui.swingViewer.DefaultView;

import java.util.Iterator;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class SDNControllerService extends Thread {

    private boolean active = true;
    private static SDNControllerService SDNControllerService = null;
    private static ControllerService controllerService = null;
    private static SDNControllerServiceJFrame csjf;

    public static synchronized SDNControllerService getInstance() {
        if (SDNControllerService == null) {
            SDNControllerService = new SDNControllerService();
            SDNControllerService.start();
        }
        csjf.setVisible(true);
        return SDNControllerService;
    }

    private SDNControllerService() {
        controllerService = ControllerService.getInstance();
        sleep(2);
        csjf = new SDNControllerServiceJFrame(this);
    }

    public void stopService() {
        active = false;
        try {
            controllerService.stopService();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        controllerService = null;
        SDNControllerService = null;
    }

    public Iterator<Integer> getActiveClients() {
        return controllerService.getActiveClients();
    }

    public FlowPolicy getFlowPolicy() {
        return controllerService.getFlowPolicy();
    }

    public void updateFlowPolicy(FlowPolicy flowPolicy) {
        controllerService.updateFlowPolicy(flowPolicy);
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
