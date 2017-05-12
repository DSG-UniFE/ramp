package test.distributedactuator;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClient;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorService;

public class DistributedActuatorClientTest {

	public static void main(String[] args) throws InterruptedException {
		RampEntryPoint.getInstance(true, null);
    	DistributedActuatorClient dac = DistributedActuatorClient.getInstance();
    	DistributedActuatorClientListener dacl = new Sensor();
		
    	String appName = "test app name";
    	dac.registerNewApp(appName, dacl);
    	
    	Thread.sleep(500);
    	
    	Thread.sleep(3000);
    	dac.leave(appName);

    	Thread.sleep(2000);
	}
	
}