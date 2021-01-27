package test.sdncontroller.digitaltwin;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerService.ControllerService;

public class DTControllerService {

    private static it.unibo.deis.lia.ramp.core.internode.sdn.controllerService.ControllerService controllerService;
    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    public static void main(String[] args) {

        ramp = RampEntryPoint.getInstance(true, null);

        System.out.println("ControllerServiceTest: registering shutdown hook");
        // Setup signal handling in order to always stop RAMP gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ramp != null && controllerService != null) {
                        System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
                        controllerService.stopService();
                        ramp.stopRamp();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }));

        controllerService = ControllerService.getInstance();

        try {
            Thread.sleep(2*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        controllerClient = ControllerClient.getInstance();

        try {
            Thread.sleep(2*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        controllerService.displayGraph();

        try {
            Thread.sleep(20*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        controllerService.takeGraphScreenshot("topologygraph.png");
    }

}
