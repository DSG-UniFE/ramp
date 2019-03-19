package test.sdncontroller;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;

public class ControllerClientTest {
	
	private static ControllerClient controllerClient;
	private static RampEntryPoint ramp;
	
	public static void main(String[] args) {
		
		ramp = RampEntryPoint.getInstance(true, null);
		
		// Wait a few second to allow the node to discover neighbors
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		// Force neighbors update to make sure to know them
		ramp.forceNeighborsUpdate();
		
		System.out.println("ControllerClientTest: registering shutdown hook");
		// Setup signal handling in order to always stop RAMP gracefully
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (ramp != null && controllerClient != null) {
						System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
						controllerClient.stopClient();
						ramp.stopRamp();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}));
		
		controllerClient = ControllerClient.getInstance();
	}

}
