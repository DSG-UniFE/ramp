package it.unibo.deis.lia.ramp.service.application;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Vector;

import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

public class TestService {
	
	public static TestService getInstance(){
		return new TestService();
	}
	
	private TestService(){
		
		/**/try {
			// test ServiceDiscovery.getServices()
			String[] dest = {InetAddress.getLocalHost().getHostAddress()};
			//String[] dest = {"137.204.57.183"};
			Vector<ServiceResponse> services = ServiceDiscovery.getServices(dest);
			System.out.println("TestService: services.size() "+services.size());
			Iterator<ServiceResponse> it = services.iterator();
			while( it.hasNext() ){
				ServiceResponse aService = it.next();
				System.out.println("TestService: aService "+aService);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}/**/
		
		/*try {
			// test ServiceDiscovery.getAllServices()
			int ttl = 5;
			int timeout = 5000;
			int nodeAmount = 5;
			Vector<ServiceResponse> responses = ServiceDiscovery.getAllServices(ttl,timeout,nodeAmount);
			System.out.println("TestService: responses.size() "+responses.size());
			Iterator<ServiceResponse> it = responses.iterator();
			while( it.hasNext() ){
				ServiceResponse aResponse = it.next();
				System.out.println("TestService: aResponse "+aResponse);
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
		}*/
		
	}

}
