
package it.unibo.deis.lia.ramp.service.management;

import java.lang.reflect.Method;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

/**
 * @author Carlo Giannelli
 */
public class ServiceManager extends Thread {

    //final protected static int SERVICEMANAGER_PROTOCOL = E2EComm.TCP;
    final protected static int SERVICEMANAGER_PROTOCOL = E2EComm.UDP;

    final public static int SERVICEMANAGER_PORT = 6400;

    /**
     * service name ==>> service descriptor
     */
    private HashMap<String, ServiceDescriptor> serviceDB = null;

    public void registerService(String serviceName, int servicePort, int protocol) {
        registerService(serviceName, servicePort, protocol, null);
    }

    public void registerService(String serviceName, int servicePort, int protocol, String qos) {
        ServiceDescriptor sd = new ServiceDescriptor(servicePort, protocol, qos);
        serviceDB.put(serviceName, sd);
        System.out.println("ServiceManager.registerService: serviceName=" + serviceName + " sd=" + sd);
    }

    public void removeService(String serviceName) {
        ServiceDescriptor sd = serviceDB.remove(serviceName);
        System.out.println("ServiceManager removeService " + serviceName + " " + sd);
    }

    public ServiceDescriptor getService(String serviceName) {
        return serviceDB.get(serviceName);
    }

    private ServiceManager() {
        serviceDB = new HashMap<String, ServiceDescriptor>();
    }

    private static ServiceManager serviceManager = null;

    public static synchronized ServiceManager getInstance(boolean forceStart) {
        if (forceStart && serviceManager == null) {
            serviceManager = new ServiceManager();
            serviceManager.start();
        }
        return serviceManager;
    }

    private BoundReceiveSocket receiveServiceManagerSocket;
    private boolean active = true;

    public void stopServiceManager() {
        active = false;
        try {
            if (receiveServiceManagerSocket != null) {
                receiveServiceManagerSocket.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        String[] services = serviceDB.keySet().toArray(new String[0]);
        for (String serviceName : services) {
            try {
                Class<?> c = Class.forName("it.unibo.deis.lia.ramp.service.application." + serviceName + "Service");
                Method mI = c.getMethod("getInstance");
                Method mS = c.getMethod("stopService");
                mS.invoke(mI.invoke(null, new Object[]{}), new Object[]{});
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("ServiceManager stop: " + serviceName);
            }
        }

        serviceDB = null;
        serviceManager = null;
    }

    @Override
    public void run() {
        System.out.println("ServiceManager.run START");
        try {
            receiveServiceManagerSocket = E2EComm.bindPreReceive(
                    ServiceManager.SERVICEMANAGER_PORT,
                    ServiceManager.SERVICEMANAGER_PROTOCOL
            );
            while (active) {
                System.out.println("ServiceManager.run receive");
                // timeout = 0 ==>> no timeout
                GenericPacket recGP = E2EComm.receive(receiveServiceManagerSocket);

                System.out.println("ServiceManager.run received gp: " + recGP);
                new ServiceManagerHandler(recGP).start();
            }
        } catch (SocketException se) {
            //se.printStackTrace();
            System.out.println("ServiceManager.run se = " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("ServiceManager.run FINISHED");
    }

    private class ServiceManagerHandler extends Thread {
        private GenericPacket recGP;

        private ServiceManagerHandler(GenericPacket recGP) {
            this.recGP = recGP;
        }

        @Override
        public void run() {
            System.out.println("ServiceManagerHandler.run START");
            try {
                if (recGP instanceof BroadcastPacket) {
                    BroadcastPacket recBP = (BroadcastPacket) recGP;
                    Object recO = E2EComm.deserialize(recBP.getBytePayload());
                    if (!(recO instanceof ServiceRequest)) {
                        System.out.println("ServiceManagerHandler: required ServiceRequest, received " + recGP.getClass().getName());
                    } else {
                        ServiceRequest recSReq = (ServiceRequest) recO;
                        System.out.println("ServiceManagerHandler: node with nodeId " + recBP.getSourceNodeId() + " required " + recSReq.getServiceName());

                        /*############# START REGION #############
                        ####### Edited by Lorenzo Donini #########
                        ########################################*/

                        String serviceName = recSReq.getServiceName();
                        if (serviceName == null) {
                            // provide every local service
                            String[] availableServices = serviceDB.keySet().toArray(new String[0]);
                            Vector<ServiceResponse> vRes = new Vector<ServiceResponse>();
                            for (int i = 0; i < availableServices.length; i++) {
                                vRes.addElement(
                                        new ServiceResponse(
                                                availableServices[i],
                                                serviceDB.get(availableServices[i])
                                        )
                                );
                            }
                            String[] source = recBP.getSource();
                            String[] dest = E2EComm.ipReverse(source);
                            E2EComm.sendUnicast(
                                    dest,
                                    recSReq.getClientPort(),
                                    ServiceManager.SERVICEMANAGER_PROTOCOL,
                                    E2EComm.serialize(vRes)
                            );
                            System.out.println("ServiceManagerHandler: sent local services to " + Arrays.toString(dest) + ":" + recSReq.getClientPort());
                        } else {
                            ServiceDescriptor sd = serviceDB.get(recSReq.getServiceName());
                            //System.out.println("ServiceManagerHandler: sd "+sd);

                            if (sd != null) {
                                // requested service available on the local node
                                ServiceResponse sRes = new ServiceResponse(recSReq.getServiceName(), sd);
                                String[] source = recBP.getSource();
                                String[] dest = E2EComm.ipReverse(source);

                                E2EComm.sendUnicast(
                                        dest,
                                        recBP.getSourceNodeId(), // destNodeId
                                        recSReq.getClientPort(),
                                        ServiceManager.SERVICEMANAGER_PROTOCOL,
                                        false,                        // ack
                                        GenericPacket.UNUSED_FIELD, // timeoutAck
                                        E2EComm.DEFAULT_BUFFERSIZE, // bufferSize
                                        GenericPacket.UNUSED_FIELD, // packetDeliveryTimeout
                                        GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                                        E2EComm.serialize(sRes)
                                );
                            } else {
                                // do nothing...
                                // System.out.println("ServiceManagerHandler: required service not avaiable on the local node ("+recSReq.getServiceName()+")");
                            }
                        }
                        /*############## END REGION ##############
                        ####### Edited by Lorenzo Donini #########
                        ########################################*/
                    }
                } else if (recGP instanceof UnicastPacket) {
                    UnicastPacket recUP = (UnicastPacket) recGP;
                    Object recO = E2EComm.deserialize(recUP.getBytePayload());
                    if (!(recO instanceof ServiceRequest)) {
                        System.out.println("ServiceManagerHandler: required ServiceRequest, received " + recGP.getClass().getName());
                    } else {
                        ServiceRequest recSReq = (ServiceRequest) recO;
                        //System.out.println("ServiceManagerHandler: required "+recSReq.getServiceName());

                        String serviceName = recSReq.getServiceName();
                        if (serviceName == null) {
                            // provide every local service
                            String[] availableServices = serviceDB.keySet().toArray(new String[0]);
                            Vector<ServiceResponse> vRes = new Vector<ServiceResponse>();
                            for (int i = 0; i < availableServices.length; i++) {
                                vRes.addElement(
                                        new ServiceResponse(
                                                availableServices[i],
                                                serviceDB.get(availableServices[i])
                                        )
                                );
                            }
                            String[] source = recUP.getSource();
                            String[] dest = E2EComm.ipReverse(source);
                            E2EComm.sendUnicast(
                                    dest,
                                    recSReq.getClientPort(),
                                    ServiceManager.SERVICEMANAGER_PROTOCOL,
                                    E2EComm.serialize(vRes)
                            );
                            System.out.println("ServiceManagerHandler: sent local services to " + Arrays.toString(dest) + ":" + recSReq.getClientPort());
                        } else {
                            ServiceDescriptor sd = serviceDB.get(serviceName);
                            //System.out.println("ServiceManagerHandler: sd "+sd);
                            if (sd != null) {
                                // requested service available on the local node
                                ServiceResponse sRes = new ServiceResponse(serviceName, sd);
                                String[] source = recUP.getSource();
                                String[] dest = E2EComm.ipReverse(source);
                                E2EComm.sendUnicast(
                                        dest,
                                        recSReq.getClientPort(),
                                        ServiceManager.SERVICEMANAGER_PROTOCOL,
                                        E2EComm.serialize(sRes)
                                );
                            } else {
                                // do nothing...
                                //System.out.println("ServiceManagerHandler: required service not avaiable on the local node ("+recSReq.getServiceName()+")");
                            }
                        }
                    }
                } else {
                    // drop packet
                    System.out.println("ServiceManagerHandler: required Broadcast/UnicastPacket, received " + recGP.getClass().getName());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
