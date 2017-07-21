package test.distributedactuator;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClient;
import it.unibo.deis.lia.ramp.util.Benchmark;


public class DistributedActuatorClientTest {

	public static void main(String[] args) throws InterruptedException {
		Benchmark.createFile();
		Benchmark.append(System.currentTimeMillis(), "started_dac", 0, 0, 0);

		RampEntryPoint ramp = RampEntryPoint.getInstance(true, null);
		Thread.sleep(2500);
		ramp.forceNeighborsUpdate();

		Thread.sleep(5000);
		ramp.forceNeighborsUpdate();

		System.out.println("DistributedActuatorClientTest, main(): registering shutdown hook");
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

    	DistributedActuatorClient dac = DistributedActuatorClient.getInstance();
    	Sensor sensor = new Sensor();
    	sensor.start();

		String appName = "QuorumBasedTest";
    	dac.registerNewApp(appName, sensor);
		System.out.println("DistributedActuatorClient, registered app: " + appName);

		System.out.println("DistributedActuatorClient: before sleep 600 seconds");
		Thread.sleep(600000);

		// dac.leave(appName);
		// System.out.println("DistributedActuatorClient, leaved app: " +
		// appName);
	}
}
