package test.distributedactuator;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

public class Benchmark {

	private static final int protocol = E2EComm.TCP;
	private static BoundReceiveSocket serviceSocket;
	
	public static void main(String[] args) {
		RampEntryPoint.getInstance(true, null);
		
		try {
			serviceSocket = E2EComm.bindPreReceive(protocol);
			ServiceManager.getInstance(false).registerService(
		    		"ProvaBenchmark",
		    		serviceSocket.getLocalPort(),
		    		protocol
				);
			
			E2EComm.sendUnicast("127.0.0.1",
					serviceSocket.getLocalPort(), 
					protocol, 
					false, 
					GenericPacket.UNUSED_FIELD,
					E2EComm.DEFAULT_BUFFERSIZE,
					10, 
					120, 
					5, 
					E2EComm.serialize("")
					);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
