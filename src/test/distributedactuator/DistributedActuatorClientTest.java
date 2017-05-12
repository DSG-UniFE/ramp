package test.distributedactuator;

import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClient;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;

public class DistributedActuatorClientTest {

	public static void main(String[] args) throws InterruptedException {
    	String appName = "test app name";
    	DistributedActuatorClient dac = DistributedActuatorClient.getInstance();
    	DistributedActuatorClientListener dacl = new Sensor();
    	dac.registerNewApp(appName, dacl);
    	Thread.sleep(15000);
    	dac.leave(appName);
	}
	
}
