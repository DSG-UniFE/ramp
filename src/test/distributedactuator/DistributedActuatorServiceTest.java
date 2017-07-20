package test.distributedactuator;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorService;

public class DistributedActuatorServiceTest {

	public static void main(String[] args) throws InterruptedException {
		RampEntryPoint ramp = RampEntryPoint.getInstance(true, null);

		System.out.println("DistributedActuatorServiceTest, main(): registering shutdown hook");
		// Setup signal handling in order to always stop RAMP gracefully
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (ramp != null) {
						System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
						ramp.stopRamp();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}));

		// ramp.startService("DistributedActuatorService");
		DistributedActuatorService das = DistributedActuatorService.getInstance();

		String appName = "QuorumBasedTest";
		das.addApp(appName);
		System.out.println("DistributedActuatorServiceTest, added app: " + appName);

		while (true) {
			Thread.sleep(1000);
		}

//		Thread.sleep(30000);
//
//		String sendCommand = "command=c,resilience=r";
//		das.sendCommand(appName, sendCommand, 1, 0);
//		System.out.println("DistributedActuatorServiceTest, sent command: " + sendCommand);
//
//		Thread.sleep(5000);
//
//    	das.removeApp(appName);
//		System.out.println("DistributedActuatorServiceTest, removed app: " + appName);
	}

}
