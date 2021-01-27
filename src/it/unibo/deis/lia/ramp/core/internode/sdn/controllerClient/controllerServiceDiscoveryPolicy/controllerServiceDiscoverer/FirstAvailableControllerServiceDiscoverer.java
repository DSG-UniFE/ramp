package it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.controllerServiceDiscoveryPolicy.controllerServiceDiscoverer;

import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

import java.util.Vector;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class FirstAvailableControllerServiceDiscoverer extends ControllerServiceDiscoverer {

    public FirstAvailableControllerServiceDiscoverer(String controllerServiceName, int timeToLive, int timeout, int serviceAmount, int pollingTimeMilliseconds) {
        super(controllerServiceName, timeToLive, timeout, serviceAmount, pollingTimeMilliseconds);
    }

    public FirstAvailableControllerServiceDiscoverer(int timeToLive, int timeout, int serviceAmount, int pollingTimeMilliseconds) {
        super(timeToLive, timeout, serviceAmount, pollingTimeMilliseconds);
    }

    @Override
    public void stopControllerServiceDiscoverer() {
        super.stopControllerServiceDiscoverer();
        System.out.println("ControllerClient FirstAvailableControllerServiceDiscoverer STOP");
    }

    @Override
    public void findControllerService() {
        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(this.timeToLive, this.controllerServiceName, this.timeout, this.serviceAmount, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*
         * Make sure that the SDNController found the first time is always found
         */
        if(serviceResponses != null && serviceResponses.size() > 0) {
            this.serviceResponse = serviceResponses.get(0);
        }
    }

    @Override
    public void heartbeatControllerService() {
        Vector<ServiceResponse> serviceResponses = null;
        boolean found = false;
        try {
            serviceResponses = ServiceDiscovery.findServices(this.timeToLive, this.controllerServiceName, this.timeout, this.serviceAmount, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*
         * Make sure that the SDNController found the first time is always found
         */
        if(serviceResponses != null && serviceResponses.size() > 0) {
            for (ServiceResponse serviceResponse : serviceResponses) {
                if(this.serviceResponse.equals(serviceResponse)) {
                    found = true;
                    break;
                }
            }
            if(found) {
                System.out.println("ControllerClient FirstAvailableControllerServiceDiscoverer: SDNController not changed");
            } else {
                /*
                 * Previously SDNControllerService not found, assigned the new first
                 * available ControllerService
                 */
                this.serviceResponse = serviceResponses.get(0);
                System.out.println("ControllerClient FirstAvailableControllerServiceDiscoverer: SDNController changed");
            }
        }
    }
}
