
package it.unibo.deis.lia.ramp.service.management;

import java.net.SocketTimeoutException;
import java.util.Vector;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.util.GeneralUtils;


/**
 * @author Carlo Giannelli
 */
public class ServiceDiscovery extends Thread {

    final private static int DEFAULT_FIND_SERVICE_TIMEOUT = 5000;

    // look for a given service via broadcast
    //public static Vector<ServiceResponse> findService(int TTL, String serviceName) throws Exception{
    //    return ServiceDiscovery.findServices(TTL, serviceName, ServiceDiscovery.DEFAULT_FIND_SERVICE_TIMEOUT, 1, null);
    //}
    public static Vector<ServiceResponse> findServices(int TTL, String serviceName, int timeout, int serviceAmount)
            throws Exception {
        return ServiceDiscovery.findServices(TTL, serviceName, timeout, serviceAmount, null);
    }

    public static Vector<ServiceResponse> findServices(int TTL, String serviceName, int timeout, int serviceAmount,
                                                       String qos) throws Exception {
        // serviceAmount==0 ==> wait until timeout!!!
        //System.out.println("Discovery.findService START");
        if (timeout <= 0) {
            throw new Exception("ServiceDiscovery: timeout must be greater than 0: " + timeout);
        }
        if (serviceAmount < 0) {
            throw new Exception("ServiceDiscovery: serviceAmount must be equal to or greater than 0: " + serviceAmount);
        }

        Vector<ServiceResponse> res = new Vector<ServiceResponse>();

        // check on the local node

        ServiceDescriptor sd = ServiceManager.getInstance(false).getService(serviceName);
        //System.out.println("ServiceManagerHandler: sd "+sd);
        if (sd != null) {
            // requested service available on the local node
            ServiceResponse localService = new ServiceResponse(serviceName, sd);
            String[] source = new String[1];
            //source[0] = InetAddress.getLocalHost().getHostAddress().replaceAll("/", "");
            source[0] = GeneralUtils.getLocalHost();
            localService.setServerDest(source);
            localService.setServerNodeId(Dispatcher.getLocalRampId());
            res.add(localService);
        }

        BoundReceiveSocket serviceClientSocket = E2EComm.bindPreReceive(ServiceManager.SERVICEMANAGER_PROTOCOL);
        ServiceRequest servReq = new ServiceRequest(serviceName, serviceClientSocket.getLocalPort(), qos);

        //System.out.println("Discovery.findService pre sendBroadcast");
        E2EComm.sendBroadcast(
                TTL,
                ServiceManager.SERVICEMANAGER_PORT,
                ServiceManager.SERVICEMANAGER_PROTOCOL,
                E2EComm.serialize(servReq)
        );

        long preReceive = System.currentTimeMillis();
        long spentTime = System.currentTimeMillis() - preReceive;
        try {
            while (spentTime < timeout && (serviceAmount == 0 || res.size() < serviceAmount)) {
                // serviceAmount==0 ==> wait until timeout!!!
                GenericPacket gp = E2EComm.receive(serviceClientSocket, (int) (timeout - spentTime));
                //System.out.println("ServiceDiscovery POST (timeout-spentTime) = "+(timeout-spentTime));
                if (gp instanceof UnicastPacket) {
                    UnicastPacket up = (UnicastPacket) gp;
                    Object o = E2EComm.deserialize(up.getBytePayload());
                    if (o instanceof ServiceResponse) {
                        ServiceResponse servResp = (ServiceResponse) o;
                        servResp.setServerDest(E2EComm.ipReverse(up.getSource()));
                        servResp.setServerNodeId(up.getSourceNodeId());
                        res.addElement(servResp);
                    } else {
                        System.out.println("ServiceDiscovery: required ServiceResponse, received " + o.getClass().getName());
                    }
                } else {
                    System.out.println("ServiceDiscovery: required UnicastPacket, received " + gp.getClass().getName());
                }
                spentTime = System.currentTimeMillis() - preReceive;
            }
        } catch (SocketTimeoutException ste) {
            // do nothing...
            //System.out.println("ServiceDiscovery SocketTimeoutException");
        }
        serviceClientSocket.close();

        return res;
    }


    // look for a given service via unicast
    public static ServiceResponse findService(String[] addresses, String serviceName) throws Exception {
        return ServiceDiscovery.findService(addresses, serviceName, ServiceDiscovery.DEFAULT_FIND_SERVICE_TIMEOUT, null);
    }

    public static ServiceResponse findService(String[] addresses, String serviceName, int timeout) throws Exception {
        return ServiceDiscovery.findService(addresses, serviceName, timeout, null);
    }

    public static ServiceResponse findService(String[] addresses, String serviceName, int timeout, String qos)
            throws Exception {
        //System.out.println("Discovery.findService START");
        if (timeout <= 0) {
            throw new Exception("ServiceDiscovery.serviceName: timeout must be greater than 0: " + timeout);
        }

        BoundReceiveSocket serviceClientSocket = E2EComm.bindPreReceive(ServiceManager.SERVICEMANAGER_PROTOCOL);
        ServiceRequest servReq = new ServiceRequest(
                serviceName,
                serviceClientSocket.getLocalPort(),
                qos
        );

        E2EComm.sendUnicast(
                addresses,
                ServiceManager.SERVICEMANAGER_PORT,
                ServiceManager.SERVICEMANAGER_PROTOCOL,
                E2EComm.serialize(servReq)
        );

        ServiceResponse res = null;
        GenericPacket gp = E2EComm.receive(serviceClientSocket, timeout);
        if (gp instanceof UnicastPacket) {
            UnicastPacket up = (UnicastPacket) gp;
            Object o = E2EComm.deserialize(up.getBytePayload());
            if (o instanceof ServiceResponse) {
                ServiceResponse servResp = (ServiceResponse) o;
                servResp.setServerDest(E2EComm.ipReverse(up.getSource()));
                res = servResp;
            } else {
                System.out.println(
                        "ServiceDiscovery.serviceName: required ServiceResponse, received " + o.getClass().getName());
            }
        } else {
            System.out.println(
                    "ServiceDiscovery.serviceName: required UnicastPacket, received " + gp.getClass().getName());
        }

        return res;
    }

    // look for every available service via unicast
    public static Vector<ServiceResponse> getServices(String[] dest) throws Exception {
        return ServiceDiscovery.getServices(dest, ServiceDiscovery.DEFAULT_FIND_SERVICE_TIMEOUT, null);
    }

    public static Vector<ServiceResponse> getServices(String[] dest, int timeout) throws Exception {
        return ServiceDiscovery.getServices(dest, timeout, null);
    }

    public static Vector<ServiceResponse> getServices(String[] dest, int timeout, String qos) throws Exception {
        // retrieve every service of a given node
        //System.out.println("ServiceDiscovery.findServices START");
        if (timeout <= 0) {
            throw new Exception("ServiceDiscovery.serviceName: timeout must be greater than 0: " + timeout);
        }

        BoundReceiveSocket serviceClientSocket = E2EComm.bindPreReceive(ServiceManager.SERVICEMANAGER_PROTOCOL);
        ServiceRequest servReq = new ServiceRequest(
                null,
                serviceClientSocket.getLocalPort(),
                qos
        );

        E2EComm.sendUnicast(
                dest,
                ServiceManager.SERVICEMANAGER_PORT,
                ServiceManager.SERVICEMANAGER_PROTOCOL,
                E2EComm.serialize(servReq)
        );

        Vector<ServiceResponse> res = new Vector<ServiceResponse>();

        System.out.println("ServiceDiscovery.findServices waiting for on " + serviceClientSocket.getLocalPort());
        GenericPacket gp = E2EComm.receive(serviceClientSocket, timeout);
        if (gp instanceof UnicastPacket) {
            UnicastPacket up = (UnicastPacket) gp;
            Object o = E2EComm.deserialize(up.getBytePayload());
            if (o instanceof Vector<?>) {

                @SuppressWarnings("unchecked")
                Vector<ServiceResponse> servRespVector = (Vector<ServiceResponse>) o;

                if (servRespVector.size() > 0) {
                    if (servRespVector.elementAt(0) instanceof ServiceResponse) {
                        for (int i = 0; i < servRespVector.size(); i++) {
                            ServiceResponse servResp = servRespVector.elementAt(i);
                            servResp.setServerDest(E2EComm.ipReverse(up.getSource()));
                            servResp.setServerNodeId(up.getSourceNodeId());
                            res.addElement(servResp);

                            //servRespVector.elementAt(i).setServerDest(E2EComm.ipReverse(up.getSource()));
                            //res.addElement(servRespVector.elementAt(i));
                        }
                    } else {
                        System.out.println(
                                "ServiceDiscovery.serviceName: required Vector<ServiceResponse>, received Vector<"
                                        + servRespVector.elementAt(0).getClass().getName() + ">");
                    }
                }
            } else {
                System.out.println("ServiceDiscovery.serviceName: required Vector<ServiceResponse>, received "
                        + o.getClass().getName());
            }
        } else {
            System.out.println(
                    "ServiceDiscovery.serviceName: required UnicastPacket, received " + gp.getClass().getName());
        }

        return res;
    }

    /* ----------------------------
    Methods added by Lorenzo Donini
    ---------------------------- */

    /**
     * Performs a service discovery procedure on all reachable nodes.
     * See {@link #findAllServices(int, int, int, String)}.
     *
     * @param TTL Time to live of the flooding request, decremented at each hop by the receiving nodes.
     *            Represents the number of maximum hops of the discovery process. In case the maximum number is
     *            reached, the request is not broadcasted any further.
     * @return Returns the found {@link ServiceResponse} objects, passed inside a {@link Vector} structure.
     * If no service is found, an empty Vector is returned.
     * @throws Exception
     */
    public static Vector<ServiceResponse> getAllServices(int TTL) throws Exception {
        return getAllServices(TTL, DEFAULT_FIND_SERVICE_TIMEOUT, 0, null);
    }

    /**
     * Performs a service discovery procedure on all reachable nodes.
     * See {@link #findAllServices(int, int, int, String)}.
     *
     * @param TTL           Time to live of the flooding request, decremented at each hop by the receiving nodse.
     *                      Represents the number of maximum hops of the discovery process. In case the maximum number is
     *                      reached, the request is not broadcasted any further.
     * @param timeout       Timeout of the request expressed in milliseconds.
     * @param serviceAmount The maximum amount of services the method needs to wait for. Once this number
     *                      of services is discovered, the method immediately returns the already found methods, not processing
     *                      further responses. If this parameter is 0, the maximum service amount is not set and the method
     *                      waits for a virtually unlimited number of responses.
     * @return Returns the found {@link ServiceResponse} objects, passed inside a {@link Vector} structure
     * If no service is found, an empty Vector is returned.
     * @throws Exception
     */
    public static Vector<ServiceResponse> getAllServices(int TTL, int timeout, int serviceAmount)
            throws Exception {
        return getAllServices(TTL, timeout, serviceAmount, null);
    }

    /**
     * Performs a service discovery operation on all the reachable nodes. This includes local multi-hop
     * RAMP nodes, as well as remote multi-hop nodes, both belonging to the same user or to other
     * known users. Remote nodes are reached passing through known RINs.
     * This method uses flooding, and can cause overhead and high bandwidth usage in networks
     * with many active RAMP nodes.
     *
     * @param TTL           Time to live of the flooding request, decremented at each hop by the receiving node.
     *                      Represents the number of maximum hops of the discovery process. In case the maximum number is
     *                      reached, the request is not broadcasted any further.
     * @param timeout       Timeout of the request expressed in milliseconds.
     * @param serviceAmount The maximum amount of services the method needs to wait for. Once this number
     *                      of services is discovered, the method immediately returns the already found methods, not processing
     *                      further responses. If this parameter is 0, the maximum service amount is not set and the method
     *                      waits for a virtually unlimited number of responses.
     * @param qos           Quality of service option. No really useful at this time.
     * @return Returns the found {@link ServiceResponse} objects, passed inside a Vector structure
     * If no service is found, an empty Vector is returned.
     * @throws Exception
     */
    public static Vector<ServiceResponse> getAllServices(int TTL, int timeout, int serviceAmount, String qos)
            throws Exception {
        if (timeout <= 0) {
            throw new Exception("ServiceDiscovery.serviceName: timeout must be greater than 0: " + timeout);
        }
        if (serviceAmount < 0) {
            throw new Exception("ServiceDiscovery: serviceAmount must be equal to " +
                    "or greater than 0: " + serviceAmount);
        }

        BoundReceiveSocket serviceClientSocket = E2EComm.bindPreReceive(ServiceManager.SERVICEMANAGER_PROTOCOL);
        ServiceRequest servReq = new ServiceRequest(null, serviceClientSocket.getLocalPort(), qos);
        E2EComm.sendBroadcast(TTL, ServiceManager.SERVICEMANAGER_PORT,
                ServiceManager.SERVICEMANAGER_PROTOCOL, E2EComm.serialize(servReq));

        long preReceive = System.currentTimeMillis();
        long spentTime = System.currentTimeMillis() - preReceive;
        Vector<ServiceResponse> result = new Vector<ServiceResponse>(); //Result object
        /* LOGGING STUFF */
        long totalTime = 0, startTime, endTime;

        try {
            while (spentTime < timeout && (serviceAmount == 0 || result.size() < serviceAmount)) {
                GenericPacket gp = E2EComm.receive(serviceClientSocket, (int) (timeout - spentTime));
                if (gp instanceof UnicastPacket) {
                    startTime = System.nanoTime(); //Storing processing start time
                    UnicastPacket up = (UnicastPacket) gp;
                    Object o = E2EComm.deserialize(up.getBytePayload());
                    if (o instanceof Vector<?>) {
                        @SuppressWarnings("unchecked")
                        Vector<ServiceResponse> serviceResp = (Vector<ServiceResponse>) o;

                        for (ServiceResponse sr : serviceResp) {
                            sr.setServerDest(E2EComm.ipReverse(up.getSource()));
                            sr.setServerNodeId(up.getSourceNodeId());
                        }
                        result.addAll(serviceResp);
                        endTime = System.nanoTime(); //Storing processing end time
                        System.err.println("ServiceDiscovery.getAllServices: Processing response took "
                                + (endTime - startTime) + " ns!");
                        totalTime += (endTime - startTime);
                    } else {
                        System.out.println("ServiceDiscovery: required ServiceResponse, " +
                                "received " + o.getClass().getName());
                    }
                } else {
                    System.out.println("ServiceDiscovery: required UnicastPacket, received "
                            + gp.getClass().getName());
                }
                spentTime = System.currentTimeMillis() - preReceive;
            }
        } catch (SocketTimeoutException ste) {
            //No neighbor nodes found, therefore there wasn't a single reply and the discovery timed out
            System.out.println("ServiceDiscovery: timed out!");
        }
        System.err.println("ServiceDiscovery.getAllServices: Processing all responses took " + totalTime + " ns!");
        return result;
    }

}
