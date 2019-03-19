package it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.controllerServiceDiscoveryPolicy.controllerServiceDiscoverer;

import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

/**
 * @author Dmitrij David Padalino Montenero
 */
public abstract class ControllerServiceDiscoverer extends Thread{

    protected String controllerServiceName;
    protected int timeToLive;
    protected int timeout;
    protected int serviceAmount;
    private int pollingTimeMilliseconds;
    protected ServiceResponse serviceResponse = null;
    protected boolean active;

    public ControllerServiceDiscoverer(String controllerServiceName, int timeToLive, int timeout, int serviceAmount, int pollingTimeMilliseconds) {
        this.controllerServiceName = controllerServiceName;
        this.timeToLive = timeToLive;
        this.timeout = timeout;
        this.serviceAmount = serviceAmount;
        this.pollingTimeMilliseconds = pollingTimeMilliseconds;
        this.serviceResponse = null;
        this.active = true;
        this.findControllerService();
        this.start();
    }

    public ControllerServiceDiscoverer(int timeToLive, int timeout, int serviceAmount, int pollingTimeMilliseconds) {
        this("SDNController", timeToLive, timeout, serviceAmount, pollingTimeMilliseconds);
    }

    public ServiceResponse getControllerService() {
        return this.serviceResponse;
    }

    public abstract void findControllerService();

    public abstract void heartbeatControllerService();

    public String getControllerServiceName() {
        return this.controllerServiceName;
    }

    public void setControllerServiceName(String controllerServiceName) {
        this.controllerServiceName = controllerServiceName;
    }

    public int getTimeToLive() {
        return this.timeToLive;
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getServiceAmount() {
        return this.serviceAmount;
    }

    public void setServiceAmount(int serviceAmount) {
        this.serviceAmount = serviceAmount;
    }

    public void stopControllerServiceDiscoverer() {
        this.active = false;
    }

    @Override
    public void run() {
        /*
         * Sleep two seconds in order to avoid that the updateManager sendTopologyUpdate is sent before the join message.
         */
        try {
            Thread.sleep(2*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("ControllerClient ControllerServiceDiscoverer START");
        while (this.active) {
            heartbeatControllerService();
            try {
                Thread.sleep(this.pollingTimeMilliseconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("ControllerClient ControllerServiceDiscoverer FINISHED");
    }
}
