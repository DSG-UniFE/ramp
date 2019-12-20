
package it.unibo.deis.lia.ramp.core.e2e;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.HeartbeatRequest;
import it.unibo.deis.lia.ramp.core.internode.HeartbeatResponse;
import it.unibo.deis.lia.ramp.core.internode.TcpDispatcher;
import it.unibo.deis.lia.ramp.core.internode.UdpDispatcher;
import org.apache.commons.io.input.ClassLoaderObjectInputStream;


/**
 * @author Carlo Giannelli
 */
public class E2EComm {

    public static final int UDP = 0;
    public static final int TCP = 1;

    public static final int DEFAULT_BUFFERSIZE = 50 * 1024;

    private static int E2ECOMM_SERVER_SOCKET_BACKLOG = 20;


    // -----------------------------------------------------------------
    // bindPreReceive: used to reserve a local port, either TCP or UDP
    // -----------------------------------------------------------------
    public static BoundReceiveSocket bindPreReceive(int protocol) throws Exception {
        return bindPreReceive(0, protocol, null, E2ECOMM_SERVER_SOCKET_BACKLOG);
    }

    public static BoundReceiveSocket bindPreReceive(int localPort, int protocol) throws Exception {
        return bindPreReceive(localPort, protocol, null, E2ECOMM_SERVER_SOCKET_BACKLOG);
    }

    public static BoundReceiveSocket bindPreReceive(int localPort, int protocol, InetAddress localSocket) throws Exception {
        return bindPreReceive(localPort, protocol, localSocket, E2ECOMM_SERVER_SOCKET_BACKLOG);
    }

    public static BoundReceiveSocket bindPreReceive(int localPort, int protocol, InetAddress localSocket, int backLog) throws Exception {
        //System.out.println("E2EComm.bindPreReceive localPort="+localPort+" protocol="+protocol+" localSocket="+localSocket+" backLog="+backLog);
        //long start = System.currentTimeMillis();
        BoundReceiveSocket res = null;
        if (protocol == E2EComm.UDP) {
            DatagramSocket ds = new DatagramSocket(localPort, localSocket);
            ds.setReuseAddress(true);
            res = new BoundReceiveSocket(ds);
        } else if (protocol == E2EComm.TCP) {
            ServerSocket ss = new ServerSocket(localPort, backLog, localSocket);
            ss.setReuseAddress(true);
            res = new BoundReceiveSocket(ss);
        } else {
            throw new Exception("Unknown protocol: must be either E2EComm.UDP or E2EComm.TCP");
        }
        /*System.out.println("\nE2EComm.bindPreReceive elapsed="
        		+(System.currentTimeMillis()-start)
        		+"\nlocalPort="+localPort+" protocol="+protocol+" localSocket="+localSocket+" backLog="+backLog+"\n");/**/
        return res;
    }


    // -----------------------------------------------------------------
    // receive: wait for a GenericPacket via either TCP or UDP
    // -----------------------------------------------------------------
    public static GenericPacket receive(BoundReceiveSocket socket) throws Exception {
        // blocking receive
        return receive(socket, 0, null);
    }

    public static GenericPacket receive(BoundReceiveSocket socket, OutputStream payloadDestination) throws Exception {
        // blocking receive
        return receive(socket, 0, payloadDestination);
    }

    public static GenericPacket receive(BoundReceiveSocket socket, int timeout) throws Exception {
        return receive(socket, timeout, null);
    }

    public static GenericPacket receive(BoundReceiveSocket socket, int timeout, OutputStream outputStreamPayloadDestination) throws Exception {
        GenericPacket resGenericPacket = null;
        if (socket == null) {
            throw new Exception("Unbound socket");
        }
        //else{
        DatagramSocket ds = socket.getDatagramSocket();
        ServerSocket ss = socket.getServerSocket();
        if (ds != null) {
            ds.setSoTimeout(timeout);
            byte[] udpBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
            DatagramPacket dp = new DatagramPacket(udpBuffer, udpBuffer.length);

            // receive
            try {
                // throws SocketTimeoutException in case of timeout
                ds.receive(dp);
            } catch (SocketTimeoutException ste) {
                //ds.close();
                throw ste;
            }

            // process payload
            byte[] data = dp.getData();
            resGenericPacket = E2EComm.deserializePacket(data, 0, dp.getLength());
        } // end DatagramSocket
        else if (ss != null) {
            ss.setSoTimeout(timeout);

            // receive
            Socket s = null;
            try {
                // throws SocketTimeoutException in case of timeout
                s = ss.accept();
            } catch (SocketTimeoutException ste) {
                throw ste;
            }

            try {
                s.setSoTimeout(timeout);
                s.setReuseAddress(true);
                InputStream is = s.getInputStream();

                // process payload
                Object receivedObject = E2EComm.readPacket(is);

                // if received only the header,
                // then wait for the payload
                ByteArrayOutputStream destBuffer = null;
                if (!(receivedObject instanceof UnicastHeader)) {
                    s.close();
                } else {
                    //System.out.println("E2EComm.receive tcp: received header");
                    // the received object is an Unicast Header:
                    // receiving the payload...

                    // receiving the payload step-by-step
                    // and forwarding read bytes to the destination output stream
                    UnicastHeader receivedUnicastHeader = (UnicastHeader) receivedObject;

                    BufferedInputStream bis = new BufferedInputStream(is, receivedUnicastHeader.getBufferSize());

                    OutputStream destination = null;
                    if (outputStreamPayloadDestination == null) {
                        // storing received data in a byte buffer
                        destBuffer = new ByteArrayOutputStream();
                        destination = new BufferedOutputStream(destBuffer, receivedUnicastHeader.getBufferSize());
                    } else {
                        // forwarding received data in to "outputStreamPayloadDestination"
                        destination = outputStreamPayloadDestination;
                    }

                    int readBytes;
                    int totBytes = 0;
                    boolean finished = false;
                    byte[] buffer = new byte[receivedUnicastHeader.getBufferSize()];

                    //System.out.println("E2EComm.receive: "  + Thread.currentThread() + " partial payload (start) ");
                    while (!finished) {
                        // attempt to read enough bytes to fulfill the buffer
                        readBytes = bis.read(buffer, 0, buffer.length);

                        //System.out.println("E2EComm partial payload read (partial): readBytes "+readBytes);
                        if (readBytes == -1) {
                            finished = true;
                        } else {
                            totBytes += readBytes;
                            destination.write(buffer, 0, readBytes);
                            destination.flush();
                            //System.out.println("E2EComm partial payload read (partial): post write readBytes "+readBytes);
                            //System.out.println("E2EComm partial payload read (partial): post write at "+System.currentTimeMillis()+" readBytes="+readBytes+" totBytes="+totBytes);
                            //System.out.println("E2EComm partial payload written (partial): buffer.length "+buffer.length);
                        }
                    }
                    //System.out.println("E2EComm.receive: " + Thread.currentThread() + " partial payload written (end): totBytes = " + totBytes);
                    s.close();
                }

                // what should I return?
                if (receivedObject instanceof UnicastHeader) {
                    if (outputStreamPayloadDestination != null) {
                        // return only unicast header (payload already sent to destination output stream)
                        resGenericPacket = (UnicastHeader) receivedObject;
                    } else {
                        // unicast packet (unicast header + payload stored in a local byte buffer)
                        resGenericPacket = new UnicastPacket((UnicastHeader) receivedObject, destBuffer.toByteArray());
                    }
                } else {
                    // either unicast packet or broadcast packet (one shot)
                    resGenericPacket = (GenericPacket) receivedObject;
                }

            } catch (Exception e) {
                try {
                    s.close();
                } catch (Exception e2) {
                }
                throw e;
            }

        } // end ServerSocket
        else {
            throw new Exception("Wrong socket");
        }

        // now "resGenericPacket" is correctly initialized:
        // UnicastPacket, UnicastHeader, or BroadcastPacket

        // 2) ACK
        if (resGenericPacket instanceof UnicastPacket) {
            UnicastPacket up = (UnicastPacket) resGenericPacket;
            boolean ack = up.isAck();
            if (ack == true) {
                //System.out.println("E2EComm.receive UnicastPacket ack requested");
                // ACK requested
                int sourcePortAck = up.getSourcePortAck();

                String[] newDest = E2EComm.ipReverse(up.getSource());

                // send "ack" to the sender exploiting the same path
                // (it could be possible even to exploit sourceNodeId)
                String payloadAck = new String("ack");
                int protocol;
                if (ds != null) {
                    protocol = E2EComm.UDP;
                } else {
                    protocol = E2EComm.TCP;
                }
                sendUnicast(
                        newDest,
                        sourcePortAck,
                        protocol,
                        E2EComm.serialize(payloadAck)
                );
            }
        } // end ack
        //}
        return resGenericPacket;
    }


    // -----------------------------------------------------------------
    // sendBroadcast
    // -----------------------------------------------------------------
    public static void sendBroadcast(int TTL, int destPort, int protocol, byte[] payload) throws Exception {
        //System.out.println("E2EComm.sendBroadcast START");

        int expiry = GenericPacket.UNUSED_FIELD;

        // 1) check parameters
        checkParameterSendBroadcast(payload, expiry);

        // 2) setup packet
        int localNodeId = Dispatcher.getLocalRampId();
        BroadcastPacket bp = new BroadcastPacket(
                (byte) TTL,
                destPort,
                localNodeId,
                expiry,
                payload
        );

        executeSendBroadcast(protocol, null, bp);
    }

    //Stefano Lanzone
    public static void sendBroadcast(int TTL, int destPort, int protocol, int expiry, byte[] payload) throws Exception {

        // 1) check parameters
        checkParameterSendBroadcast(payload, expiry);

        // 2) setup packet
        int localNodeId = Dispatcher.getLocalRampId();
        BroadcastPacket bp = new BroadcastPacket(
                (byte) TTL,
                destPort,
                localNodeId,
                expiry,
                payload
        );

        executeSendBroadcast(protocol, null, bp);
    }

    private static void checkParameterSendBroadcast(byte[] payload, int expiry) throws Exception {
        // check maximum payload size
        int payloadSize;
        if (payload != null) {
            payloadSize = payload.length;
        } else {
            payloadSize = 0;
        }
        if (payloadSize > BroadcastPacket.MAX_BROADCAST_PAYLOAD) {
            throw new Exception("Maximum payload size of BroadcastPacket is " + BroadcastPacket.MAX_BROADCAST_PAYLOAD + " but current payloadSize is " + payloadSize);
        }

        //Stefano Lanzone
        if (expiry != GenericPacket.UNUSED_FIELD && expiry <= 0) {
            throw new Exception("expiry must be greater than 0 but current expiry = " + expiry);
        }
    }

    private static void executeSendBroadcast(int protocol, Set<Integer> exploredNodeIdList, BroadcastPacket bp) throws UnknownHostException, Exception {
        // 3) send packet to local Dispatcher
        if (protocol == E2EComm.UDP) {
            //System.out.println("E2EComm.sendBroadcast udp");

            InetAddress localhost = InetAddress.getLocalHost();
            //InetAddress localhost = InetAddress.getByName("127.0.0.1");
            System.out.println("E2EComm.sendBroadcast udp localhost = " + localhost);
            //(new UdpDispatcher.UdpDispatcherHandler(bp, localhost)).start();
            UdpDispatcher.asyncDispatchUdpGenericPacket(bp, localhost);

            //System.out.println("E2EComm.sendBroadcast udp end");
        } else if (protocol == E2EComm.TCP) {
            //System.out.println("E2EComm.sendBroadcast tcp");

            // send broadcast packet via local dispatcher
            InetAddress localhost = InetAddress.getLocalHost();
            //(new TcpDispatcher.TcpDispatcherHandler(bp, localhost)).start();

            //Stefano Lanzone
            if (exploredNodeIdList == null)
                TcpDispatcher.asyncDispatchTcpGenericPacket(bp, localhost);
            else
                TcpDispatcher.asyncDispatchTcpGenericPacket(bp, localhost, exploredNodeIdList);

            //System.out.println("E2EComm.sendBroadcast tcp end");
        } else {
            throw new Exception("Unknown protocol: must be either E2EComm.UDP or E2EComm.TCP: " + protocol);
        }
    }

    //Stefano Lanzone
    //sendBroadcast with input parameters for Opportunistic Networking
    public static void sendBroadcast(int protocol, Set<Integer> exploredNodeIdList, BroadcastPacket bp) throws Exception {

        if (bp == null)
            throw new Exception("BroadcastPacket is null!");
        if (exploredNodeIdList == null)
            throw new Exception("BroadcastPacket exploredNodeIdList is null!");

        executeSendBroadcast(protocol, exploredNodeIdList, bp);
    }


    // -----------------------------------------------------------------
    // sendUnicast: receiver via nodeId, payload as byte[]
    // -----------------------------------------------------------------
    /*public static boolean sendUnicastDestNodeId(int destNodeId, int destPort, int protocol, byte[] payload) throws Exception{
        // destNodeId!!! static sender-side version
        boolean res;
        boolean ack = false;
        int bufferSize = E2EComm.DEFAULT_BUFFERSIZE;
        res = sendUnicastDestNodeId(
                destNodeId,
                destPort,
                protocol,
                ack,
                GenericPacket.UNUSED_FIELD, // timeoutAck
                bufferSize,
                GenericPacket.UNUSED_FIELD, // packetDeliveryTimeout
                GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                payload
        );
        return res;
    }
    public static boolean sendUnicastDestNodeId(int destNodeId, int destPort, int protocol, boolean ack, int timeoutAck, int bufferSize, int packetDeliveryTimeout, int packetTimeoutConnect, byte[] payload) throws Exception{
        // destNodeId!!! static sender-side version
        boolean res;
        Vector<ResolverPath> paths = Resolver.getInstance(false).resolveBlocking(destNodeId);
        if(paths!=null && paths.size()>0){
            ResolverPath mostRecent = paths.elementAt(0);
            for(int i=1; i<paths.size(); i++){
                ResolverPath current = paths.elementAt(i);
                if(current.getLastUpdate()>mostRecent.getLastUpdate()){
                    mostRecent = current;
                }
            }
            res = sendUnicast(
                    mostRecent.getPath(),
                    "".hashCode(), // destNodeId
                    destPort,
                    protocol,
                    ack,
                    timeoutAck,
                    bufferSize,
                    packetDeliveryTimeout,
                    packetTimeoutConnect,
                    payload
            );
        }
        else{
            res = false;
        }
        return res;
    }*/


    // -----------------------------------------------------------------
    // sendUnicast: receiver via IP address sequence, payload as byte[]
    // -----------------------------------------------------------------
    public static boolean sendUnicast(String[] dest, int destPort, int protocol, byte[] payload) throws Exception {
        boolean res;
        res = sendUnicast(
                dest,
                "".hashCode(),                // destNodeId
                destPort,
                protocol,
                false,                        // ack
                GenericPacket.UNUSED_FIELD, // timeoutAck
                E2EComm.DEFAULT_BUFFERSIZE, // bufferSize
                GenericPacket.UNUSED_FIELD, // packetDeliveryTimeout
                GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                payload
        );
        return res;
    }

    //Stefano Lanzone
    //sendUnicast with input parameters for Opportunistic Networking
    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int timeWait,
            int expiry,                     // if != -1 OPPORTUNISTIC NETWORKING
            int packetTimeoutConnect,    // inter-node socket connect timeout (only TCP)
            byte[] payload
    ) throws Exception {

        boolean res = true;
        int retry = GenericPacket.UNUSED_FIELD;
        int packetDeliveryTimeout = GenericPacket.UNUSED_FIELD;
        int flowId = GenericPacket.UNUSED_FIELD;
        long dataType = GenericPacket.UNUSED_FIELD;

        // check parameters
        checkParameterSendUnicast(dest, destNodeId, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, expiry, flowId, dataType);
        // execute
        res = executeSendUnicast(dest, destNodeId, destPort, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, packetTimeoutConnect, payload, retry, timeWait, expiry, flowId, dataType, null);

        return res;
    }

    //Stefano Lanzone
    //sendUnicast with UnicastPacket input parameter
    public static boolean sendUnicast(int protocol, UnicastPacket up) throws Exception {
        boolean res = true;

        if (up == null) {
            throw new Exception("UnicastPacket is null!");
        }

        // check parameters
        checkParameterSendUnicast(up.getDest(), up.getDestNodeId(), protocol, false, GenericPacket.UNUSED_FIELD, E2EComm.DEFAULT_BUFFERSIZE, GenericPacket.UNUSED_FIELD, up.getExpiry(), GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD);

        res = executeSendUnicast(up.getDest(), up.getDestNodeId(), up.getDestPort(), protocol, false, GenericPacket.UNUSED_FIELD, up.getBufferSize(), GenericPacket.UNUSED_FIELD, up.getConnectTimeout(), up.getBytePayload(), up.getRetry(), up.getTimeWait(), up.getExpiry(), GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, up);

        return res;
    }

    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int packetDeliveryTimeout,        // delay-tolerant messaging
            int packetTimeoutConnect,    // inter-node socket connect timeout (only TCP)
            byte[] payload
    ) throws Exception {

        //System.out.println("E2EComm.sendUnicast byte[] payload.length="+payload.length);

        boolean res = true;
        int retry = GenericPacket.UNUSED_FIELD;
        int timeWait = GenericPacket.UNUSED_FIELD;
        int expiry = GenericPacket.UNUSED_FIELD;
        int flowId = GenericPacket.UNUSED_FIELD;
        long dataType = GenericPacket.UNUSED_FIELD;

        // check parameters
        checkParameterSendUnicast(dest, destNodeId, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, expiry, flowId, dataType);
        // execute
        res = executeSendUnicast(dest, destNodeId, destPort, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, packetTimeoutConnect, payload, retry, timeWait, expiry, flowId, dataType, null);

        return res;
    }

    // Alessandro Dolci
    // sendUnicast with flowId input parameter
    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int timeWait,
            int expiry,                     // if != -1 OPPORTUNISTIC NETWORKING
            int packetTimeoutConnect,    // inter-node socket connect timeout (only TCP)
            int flowId,
            byte[] payload
    ) throws Exception {

        boolean res = true;
        int retry = GenericPacket.UNUSED_FIELD;
        int packetDeliveryTimeout = GenericPacket.UNUSED_FIELD;
        long dataType = GenericPacket.UNUSED_FIELD;

        // check parameters
        checkParameterSendUnicast(dest, destNodeId, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, expiry, flowId, dataType);
        // execute
        res = executeSendUnicast(dest, destNodeId, destPort, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, packetTimeoutConnect, payload, retry, timeWait, expiry, flowId, dataType, null);

        return res;
    }

    // Dmitrij David Padalino Montenero
    public static boolean sendUnicast(String[] dest, int destNodeId, int destPort, int protocol, int flowId, byte[] payload) throws Exception {
        boolean res;
        res = sendUnicast(
                dest,
                destNodeId,
                destPort,
                protocol,
                false,
                GenericPacket.UNUSED_FIELD,
                E2EComm.DEFAULT_BUFFERSIZE,
                GenericPacket.UNUSED_FIELD,
                GenericPacket.UNUSED_FIELD,
                GenericPacket.UNUSED_FIELD,
                flowId,
                GenericPacket.UNUSED_FIELD,
                payload
        );
        return res;
    }

    // Dmitrij David Padalino Montenero
    // sendUnicast with dataType input parameter
    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int timeWait,
            int expiry,                     // if != -1 OPPORTUNISTIC NETWORKING
            int packetTimeoutConnect,    // inter-node socket connect timeout (only TCP)
            int flowId,
            long dataType,
            byte[] payload
    ) throws Exception {

        boolean res = true;
        int retry = GenericPacket.UNUSED_FIELD;
        int packetDeliveryTimeout = GenericPacket.UNUSED_FIELD;

        // check parameters
        checkParameterSendUnicast(dest, destNodeId, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, expiry, flowId, dataType);
        // execute
        res = executeSendUnicast(dest, destNodeId, destPort, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, packetTimeoutConnect, payload, retry, timeWait, expiry, flowId, dataType, null);

        return res;
    }

    private static boolean executeSendUnicast(String[] dest, int destNodeId, int destPort, int protocol, boolean ack, int timeoutAck, int bufferSize, int packetDeliveryTimeout,
                                              int packetTimeoutConnect, byte[] payload, int retry, int timeWait, int expiry, int flowId, long dataType, UnicastPacket gp) throws Exception, SocketException, UnknownHostException, IOException {

        boolean res = true;
        UnicastPacket up;
        UnicastHeader uh;
        int payloadSize;

        if (gp == null) {
            if ((bufferSize != GenericPacket.UNUSED_FIELD) && (bufferSize == 0)) {
                bufferSize = E2EComm.DEFAULT_BUFFERSIZE;
            }

            if (payload != null) {
                payloadSize = payload.length;
            } else {
                payloadSize = 0;
            }

            // packetDeliveryTimeout (seconds)
            if (packetDeliveryTimeout != GenericPacket.UNUSED_FIELD) {
                // delay-tolerant
                if (payloadSize >= UnicastPacket.MAX_DELAY_TOLERANT_PAYLOAD) {
                    throw new Exception("Maximum payload size of Delay-Tolerant UnicastPacket is " + UnicastPacket.MAX_DELAY_TOLERANT_PAYLOAD + " but current payloadSize is " + payloadSize);
                }
                if (payloadSize < 100 * 1024) {
                    retry = 15;
                } else {
                    retry = 8;
                }
                timeWait = (packetDeliveryTimeout * 1000) / retry;
            }

            // 1) setup packet
            int localNodeId = Dispatcher.getLocalRampId();
            uh = new UnicastHeader(
                    dest,
                    destPort,
                    destNodeId,
                    localNodeId,
                    ack,
                    timeoutAck,
                    (byte) 0,    // currentHop set to 0
                    bufferSize,
                    (byte) retry,
                    timeWait,
                    expiry,            // expiry, if != -1 OPPORTUNISTIC NETWORKING
                    packetTimeoutConnect,
                    flowId, // Alessandro Dolci
                    dataType // Dmitrij David Padalino Montenero
            );

            up = new UnicastPacket(uh, payload);
        } else {
            up = gp;
            uh = up.getHeader();
            payloadSize = up.getBytePayload().length;
        }

        // 2) send packet via local Dispatcher
        DatagramSocket dsAck = null;
        ServerSocket ssAck = null;
        if (protocol == E2EComm.UDP) {

            // 1) maximum payload size
            if (payloadSize > GenericPacket.MAX_UDP_PAYLOAD) {
                System.out.println("E2EComm: Maximum payload size of UDP-baseed UnicastPacket is " + GenericPacket.MAX_UDP_PAYLOAD + " but current payloadSize is " + payloadSize);
                throw new Exception("Maximum payload size of UDP-baseed UnicastPacket is " + GenericPacket.MAX_UDP_PAYLOAD + " but current payloadSize is " + payloadSize);
            }

            // check 255.255.255.255 is not in dest[]
            if (dest != null) {
                for (int i = 0; i < dest.length; i++) {
                    if (dest[i].equals("255.255.255.255")) {
                        throw new Exception("E2EComm.sendUnicast: 255.255.255.255 not allowed");
                    }
                }
            }

            if (ack) {
                dsAck = new DatagramSocket();
                dsAck.setReuseAddress(true);
                up.setSourcePortAck(dsAck.getLocalPort());
            }
            InetAddress localhost = InetAddress.getLocalHost();
            System.out.println("E2EComm.sendUnicast udp localhost = " + localhost);
            UdpDispatcher.asyncDispatchUdpGenericPacket(up, localhost);

        } else if (protocol == E2EComm.TCP) {
            //System.out.println("E2EComm.sendUnicast tcp start");

            // use bufferSize to decide if sending
            // the whole packet
            // or
            // first only the header and then only the payload
            if (ack) {
                ssAck = new ServerSocket();
                ssAck.setReuseAddress(true);
                uh.setSourcePortAck(ssAck.getLocalPort());
            }

            // send unicast packet to the local Dispatcher

            InetAddress localhost = InetAddress.getLocalHost();
            //System.out.println("E2EComm.sendUnicast tcp localhost = "+localhost);
            //(new TcpDispatcher.TcpDispatcherHandler(up, localhost)).start();
            TcpDispatcher.asyncDispatchTcpGenericPacket(up, localhost);

            //System.out.println("E2EComm.sendUnicast tcp s.getSendBufferSize() = "+socketToLocalhost.getSendBufferSize());
            /*

             MS Windows issue!!!
             In case of "Address already in use: connect"
             there could be two issues...

             1) first of all reduce the TIME_WAIT windows socket value
                a) Start Registry Editor.
                b) locate the following subkey, and then click Parameters:
                    HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters
                c) On the Edit menu, click New, and then add the following registry entry:
                    Value Name: TcpTimedWaitDelay
                    Value Type: DWORD
                    Value data: 0x1E or 30 (i.e., 30 seconds)
                    Valid Range: 30-300 seconds (decimal)
                    Default: 0xF0 ( 240 seconds = 4 minutes )

             2) if it does not resolve the problem
                see the following topic Microsoft Knowledge Base Article 196271:
                "When you try to connect from TCP ports greater than 5000
                you receive the error 'WSAENOBUFS (10055)'"

                 a) Start Registry Editor.
                 b) Locate the following subkey in the registry, and then click Parameters:
                    HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters
                 c) On the Edit menu, click New, and then add the following registry entry:
                        Value Name: MaxUserPort
                        Value Type: DWORD
                        Value data: 65534
                        Valid Range: 5000-65534 (decimal)
                        Default: 0x1388 (5000 decimal)
             */
        } else {
            throw new Exception("Unknown protocol: must be either E2EComm.UDP or E2EComm.TCP");
        }

        // 3) ack
        if (ack == true) {
            // receive ack
            GenericPacket gpAck = null;
            try {
                if (protocol == E2EComm.UDP) {
                    System.out.println("E2EComm.sendUnicast udp ack localPort = " + uh.getSourcePortAck());
                    byte[] udpBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
                    //DatagramSocket ds = new DatagramSocket(uh.getSourcePortAck());
                    DatagramPacket dp = new DatagramPacket(udpBuffer, udpBuffer.length);
                    dsAck.setSoTimeout(timeoutAck);

                    // receive and close
                    dsAck.receive(dp); // throws SocketTimeoutException in case of timeout
                    dsAck.close();

                    // process payload
                    // from byte to object
                    gpAck = E2EComm.deserializePacket(dp.getData());
                } else if (protocol == E2EComm.TCP) {
                    //System.out.println("E2EComm.sendUnicast tcp ack localPort = "+up.getSourcePortAck());
                    ssAck = new ServerSocket(up.getSourcePortAck(), 2);
                    //ssAck.setReuseAddress(true);
                    ssAck.setSoTimeout(timeoutAck);

                    // receive and close
                    Socket s = ssAck.accept(); // throws SocketTimeoutException in case of timeout
                    ssAck.close();

                    // process payload
                    try {
                        InputStream is = s.getInputStream();
                        gpAck = E2EComm.readPacket(is);
                    } catch (Exception e) {
                        throw e;
                    } finally {
                        try {
                            s.close();
                        } catch (Exception e2) {
                        }
                    }
                }
            } catch (java.net.SocketTimeoutException ste) {
                System.out.println("E2EComm.sendUnicast receive ack: ste = " + ste);
                if (ssAck != null)
                    ssAck.close();
                res = false;
            }

            if (res == true) {
                // check it is actually an ack
                if (gpAck instanceof UnicastPacket) {
                    UnicastPacket upAck = (UnicastPacket) gpAck;

                    // XXX ??? check the sender of the ack is the same node in dest (an id in the ack???)

                    Object ackPayload = E2EComm.deserialize(upAck.getBytePayload());
                    if (ackPayload instanceof java.lang.String) {
                        String ackPayloadString = (String) ackPayload;
                        if (!ackPayloadString.equals("ack")) {
                            System.out.println("E2EComm.sendUnicast receive ack FALSE: ackPayloadString = " + ackPayloadString);
                            res = false;
                        }
                    } else {
                        System.out.println("E2EComm.sendUnicast receive ack FALSE: ackPayload class = " + ackPayload.getClass().getName());
                        System.out.println("E2EComm.sendUnicast receive ack FALSE: ackPayload class = " + ackPayload.getClass().getSimpleName());
                        res = false;
                    }
                } else {
                    System.out.println("E2EComm.sendUnicast receive ack FALSE: gpAck class = " + gpAck.getClass().getName());
                    res = false;
                }
            }
        }
        return res;
    }

    private static void checkParameterSendUnicast(String[] dest, int destNodeId, int protocol, boolean ack, int timeoutAck, int bufferSize, int packetDeliveryTimeout, int expiry, int flowId, long dataType) throws Exception {

        if (bufferSize != GenericPacket.UNUSED_FIELD && bufferSize != 0 && (bufferSize < 5 * 1024 || bufferSize > 1024 * 1024)) {
            throw new Exception("bufferSize must be in the [5KB,1MB] range but current bufferSize = " + bufferSize);
        }
        if ((ack == true) && (timeoutAck < 0)) {
            throw new Exception("timeoutAck must be equal to or greater than 0 but current timeoutAck = " + timeoutAck);
        }
        if (packetDeliveryTimeout != GenericPacket.UNUSED_FIELD && packetDeliveryTimeout <= 0) {
            throw new Exception("packetDeliveryTimeout must be greater than 0 but current packetDeliveryTimeout = " + packetDeliveryTimeout);
        }
        if (packetDeliveryTimeout > 0 && protocol != E2EComm.TCP) {
            throw new Exception("packetDeliveryTimeout is greater than 0 but protocol is not E2EComm.TCP (protocol=" + protocol + ")");
        }
        if (packetDeliveryTimeout > 0 && destNodeId == "".hashCode()) {//(destNodeId==null || destNodeId.equals(""))){
            throw new Exception("packetDeliveryTimeout is greater than 0 but destNodeId is empty");//either null or empty");
        }
        if (packetDeliveryTimeout > 0 && bufferSize != GenericPacket.UNUSED_FIELD) {
            throw new Exception("packetDeliveryTimeout is greater than 0 but bufferSize is enabled: bufferSize=" + bufferSize);
        }
        //if( (destNodeId==null || destNodeId.equals("")) && (dest==null || dest.length==0) ){
        if ((destNodeId == "".hashCode()) && (dest == null || dest.length == 0)) {
            throw new Exception("both dest and destNodeId are incorrect: dest=" + (dest == null ? dest : Arrays.toString(dest)) + " destNodeId=" + destNodeId);
        }
        //Stefano Lanzone
        if (expiry != GenericPacket.UNUSED_FIELD && expiry <= 0) {
            throw new Exception("expiry must be greater than 0 but current expiry = " + expiry);
        }
    }

    // -----------------------------------------------------------------
    // sendUnicast: receiver via nodeId, payload as input stream
    // -----------------------------------------------------------------
    /*public static boolean sendUnicastDestNodeId(int destNodeId, int destPort, int protocol, InputStream payload) throws Exception{
        // destNodeId!!! static sender-side version
        boolean res;
        boolean ack = false;
        int bufferSize = E2EComm.DEFAULT_BUFFERSIZE;
        res = sendUnicastDestNodeId(
                destNodeId,
                destPort,
                protocol,
                ack,
                GenericPacket.UNUSED_FIELD, // timeoutAck
                bufferSize,
                GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                payload
        );
        return res;
    }
    public static boolean sendUnicastDestNodeId(int destNodeId, int destPort, int protocol, boolean ack, int timeoutAck, int bufferSize, //int packetDeliveryTimeout,
     int packetTimeoutConnect, InputStream payload) throws Exception{
        // destNodeId!!! static sender-side version
        boolean res;
        Vector<ResolverPath> paths = Resolver.getInstance(false).resolveBlocking(destNodeId);
        if(paths!=null && paths.size()>0){
            ResolverPath mostRecent = paths.elementAt(0);
            for(int i=1; i<paths.size(); i++){
                ResolverPath current = paths.elementAt(i);
                if(current.getLastUpdate()>mostRecent.getLastUpdate()){
                    mostRecent = current;
                }
            }
            res = sendUnicast(
                    mostRecent.getPath(),
                    "".hashCode(), // destNodeId
                    destPort,
                    protocol,
                    ack,
                    timeoutAck,
                    bufferSize,
                    packetTimeoutConnect,
                    null,
                    payload

            );
        }
        else{
            res = false;
        }
        return res;
    }*/


    // -----------------------------------------------------------------
    // sendUnicast: receiver via IP address sequence, payload as input stream
    // -----------------------------------------------------------------
    public static boolean sendUnicast(String[] dest, int destPort, int protocol, InputStream payload) throws Exception {
        boolean res;
        boolean ack = false;
        int bufferSize = E2EComm.DEFAULT_BUFFERSIZE;
        int timeWait = GenericPacket.UNUSED_FIELD;
        int expiry = GenericPacket.UNUSED_FIELD;
        res = sendUnicast(
                dest,
                "".hashCode(),                // destNodeId
                destPort,
                protocol,
                ack,
                GenericPacket.UNUSED_FIELD, // timeoutAck
                bufferSize,
                timeWait,
                expiry,
                GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                payload

        );
        return res;
    }

    //Stefano Lanzone
    //sendUnicast with input parameters for Opportunistic Networking
    public static boolean sendUnicast(String[] dest, int destNodeId, int destPort, int protocol, int timeWait, int expiry, InputStream payload) throws Exception {
        boolean res;
        boolean ack = false;
        int bufferSize = E2EComm.DEFAULT_BUFFERSIZE;
        res = sendUnicast(
                dest,
                destNodeId,                // destNodeId
                destPort,
                protocol,
                ack,
                GenericPacket.UNUSED_FIELD, // timeoutAck
                bufferSize,
                timeWait,
                expiry,
                GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                payload

        );
        return res;
    }

//    public static boolean sendUnicast(String[] dest, int destPort, int protocol,InetAddress bindAddress, InputStream payload ) throws Exception{
//        boolean res;
//        res = sendUnicast(
//                dest,
//                "".hashCode(), 				// destNodeId
//                destPort,
//                protocol,
//                false,						// ack
//                GenericPacket.UNUSED_FIELD, // timeoutAck
//                E2EComm.DEFAULT_BUFFERSIZE,	// bufferSize
//                GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
//                payload
//
//        );
//        return res;
//    }

    // Alessandro Dolci
    // Adapts the two previous sendUnicast methods to the new signature of the last one
    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int timeWait,
            int expiry,                     // if != -1 OPPORTUNISTIC NETWORKING
            int packetTimeoutConnect,        // inter-node socket connect timeout (only for TCP)
            InputStream payload
    ) throws Exception {
        int flowId = GenericPacket.UNUSED_FIELD;
        boolean res = sendUnicast(
                dest,
                destNodeId,
                destPort,
                protocol,
                ack,
                timeoutAck,
                bufferSize,
                timeWait,
                expiry,
                packetTimeoutConnect,
                flowId,
                payload
        );
        return res;
    }

    // Dmitrij David Padalino Montenero
    // Adapts the three previous sendUnicast methods to the new signature of the last one
    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int timeWait,
            int expiry,                     // if != -1 OPPORTUNISTIC NETWORKING
            int packetTimeoutConnect,        // inter-node socket connect timeout (only for TCP)
            int flowId,
            InputStream payload
    ) throws Exception {
        long dataType = GenericPacket.UNUSED_FIELD;
        boolean res = sendUnicast(
                dest,
                destNodeId,
                destPort,
                protocol,
                ack,
                timeoutAck,
                bufferSize,
                timeWait,
                expiry,
                packetTimeoutConnect,
                flowId,
                dataType,
                payload
        );
        return res;
    }

    public static boolean sendUnicast(
            String[] dest,
            int destNodeId,
            int destPort,
            int protocol,
            boolean ack, int timeoutAck,
            int bufferSize,
            int timeWait,
            int expiry,                     // if != -1 OPPORTUNISTIC NETWORKING
            int packetTimeoutConnect,        // inter-node socket connect timeout (only for TCP)
            int flowId, // Alessandro Dolci
            long dataType, // Dmitrij David Padalino Montenero
            InputStream payload
    ) throws Exception {
        //System.out.println("E2EComm.sendUnicast InputStream");

        boolean res = true;
        int retry = GenericPacket.UNUSED_FIELD;
        int packetDeliveryTimeout = GenericPacket.UNUSED_FIELD;

        if (protocol != E2EComm.TCP) {
            throw new Exception("InputStream but protocol is not E2EComm.TCP (protocol=" + protocol + ")");
        }

        // check parameters
        checkParameterSendUnicast(dest, destNodeId, protocol, ack, timeoutAck, bufferSize, packetDeliveryTimeout, expiry, flowId, dataType);


        if ((bufferSize != GenericPacket.UNUSED_FIELD) && (bufferSize == 0)) {
            bufferSize = E2EComm.DEFAULT_BUFFERSIZE;
        }

        // 1) setup packet
        int localNodeId = Dispatcher.getLocalRampId();
        UnicastHeader uh = new UnicastHeader(
                dest,
                destPort,
                destNodeId,
                localNodeId,
                ack,
                timeoutAck,
                (byte) 0,                            // currentHop set to 0
                bufferSize,
                (byte) retry,    // (byte)retry,
                timeWait,            // timeWait,
                expiry,            // expiry, NON OPORTUNISTIC NETWORKING
                packetTimeoutConnect,
                flowId, // Alessandro Dolci
                dataType // Dmitrij David Padalino Montenero
        );

        ServerSocket ssAck = null;
        if (ack) {
            ssAck = new ServerSocket();
            uh.setSourcePortAck(ssAck.getLocalPort());
            ssAck.setReuseAddress(true);
        }

        // send unicast packet to the local Dispatcher

        InetAddress localhost = InetAddress.getLocalHost();
        //System.out.println("E2EComm.sendUnicast tcp localhost = "+localhost);

        // In this case, sendUnicast could be synchronous, i.e.,
        // E2EComm.sendUnicast returns only when TcpDispatcherHandler returns
        // and thus only when the InputStream "payload" closes
        //(new TcpDispatcher.TcpDispatcherHandler(uh, payload, localhost)).run();	// synchronous

        // send unicast header via the local Dispatcher
        //(new TcpDispatcher.TcpDispatcherHandler(uh, payload, localhost)).start(); 	// asynchronous
        TcpDispatcher.asyncDispatchTcpGenericPacket(uh, payload, localhost);

        // 3) ack
        if (ack == true) {
            // receive ack
            GenericPacket gpAck = null;
            try {
                //System.out.println("E2EComm.sendUnicast tcp ack localPort = "+up.getSourcePortAck());
                ssAck = new ServerSocket(uh.getSourcePortAck(), 2);
                //ssAck.setReuseAddress(true);
                ssAck.setSoTimeout(timeoutAck);

                // receive and close
                Socket s = ssAck.accept(); // throws SocketTimeoutException in case of timeout
                ssAck.close();

                // process payload
                try {
                    InputStream is = s.getInputStream();
                    gpAck = E2EComm.readPacket(is);
                } catch (Exception e) {
                    throw e;
                } finally {
                    try {
                        s.close();
                    } catch (Exception e2) {
                    }
                }
            } catch (java.net.SocketTimeoutException ste) {
                System.out.println("E2EComm.sendUnicast receive ack: ste = " + ste);
                if (ssAck != null)
                    ssAck.close();
                res = false;
            }

            if (res == true) {
                // check it is actually an ack
                if (gpAck instanceof UnicastPacket) {
                    UnicastPacket upAck = (UnicastPacket) gpAck;

                    // XXX ??? check the sender of the ack is the same node in dest (an id in the ack???)

                    Object ackPayload = E2EComm.deserialize(upAck.getBytePayload());
                    if (ackPayload instanceof java.lang.String) {
                        String ackPayloadString = (String) ackPayload;
                        if (!ackPayloadString.equals("ack")) {
                            System.out.println("E2EComm.sendUnicast receive ack FALSE: ackPayloadString = " + ackPayloadString);
                            res = false;
                        }
                    } else {
                        System.out.println("E2EComm.sendUnicast receive ack FALSE: ackPayload class = " + ackPayload.getClass().getName());
                        System.out.println("E2EComm.sendUnicast receive ack FALSE: ackPayload class = " + ackPayload.getClass().getSimpleName());
                        res = false;
                    }
                } else {
                    System.out.println("E2EComm.sendUnicast receive ack FALSE: gpAck class = " + gpAck.getClass().getName());
                    res = false;
                }
            }
        }

        return res;
    }

    // -----------------------------------------------------------------
    // general purpose utility methods
    // -----------------------------------------------------------------

    // used to reverse "source" header field
    public static String[] ipReverse(String[] dest) {
        String[] res = new String[dest.length];
        for (int i = 0; i < dest.length; i++) {
            res[i] = dest[dest.length - i - 1];
        }
        return res;
    }

    // used to get the "best" size of unicast packet payload chunks
    public static int bestBufferSize(int pathLength, long packetSize) {
        int res = E2EComm.DEFAULT_BUFFERSIZE;
        if (pathLength == 1) {
            // single-hop path
            packetSize = GenericPacket.UNUSED_FIELD;
        } else {
            // multi-hop path
            if (packetSize >= 5 * 1024 * 1024) {
                // large file (>=5MB)
                res = 100 * 1024;
            } else if (packetSize <= 100 * 1024) {
                // small file (<=100KB)
                if (pathLength <= 2) {
                    // short path
                    res = 10 * 1024;
                } else {
                    // long path
                    res = 5 * 1024;
                }
            }
        }
        return res;
    }


    // -----------------------------------------------------------------
    // serialization methods
    // -----------------------------------------------------------------

    // object size if serialized via Java serialization mechanism
    public static int objectSize(Object object) {
        int objectSize = -1;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            ObjectOutputStream out = new ObjectOutputStream(baos);

            out.writeObject(object);
            out.close();
            objectSize = baos.toByteArray().length;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectSize;
    }

    // RAMP packet size if serialized via either protobuf or Java default mechanism
    public static int objectSizePacket(GenericPacket gp) {
        int objectSize = -1;
        if (RampEntryPoint.protobuf) {
            objectSize = gp.toProtosByteArray().length;
        } else {
            objectSize = E2EComm.objectSize(gp);
        }
        return objectSize;
    }

    public static byte[] serialize(Object obj) throws Exception {
        byte[] serialized;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ObjectOutputStream out = new ObjectOutputStream(baos);

        out.writeObject(obj);
        out.close();
        serialized = baos.toByteArray();
        return serialized;
    }

    public static byte[] serializePacket(GenericPacket gp) throws Exception {
        byte[] serialized;
        if (RampEntryPoint.protobuf) {
            byte[] protoBytes = gp.toProtosByteArray();
            serialized = new byte[protoBytes.length + 1];
            serialized[0] = gp.getPacketId();
            System.arraycopy(protoBytes, 0, serialized, 1, protoBytes.length);
        } else {
            serialized = E2EComm.serialize(gp);
        }
        return serialized;
    }

    public static Object deserialize(byte[] bytes) throws Exception {
        return E2EComm.deserialize(bytes, 0, bytes.length);
    }

    public static Object deserialize(byte[] bytes, int offset, int length) throws Exception {
        //System.out.println("E2EComm.deserialize bytes.length="+bytes.length);
        Object deserialized;
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes, offset, length);

        /*
         * Dmitrij David Padalino Montenero
         *
         * Due to the introduction of RampClassLoader that has as parent the SystemClassLoader by using
         * ClassLoaderObjectInputStream in place of ObjectInputStream from now on it is possible
         * to resolve classes loaded at runtime that do not belong to the startup classpath.
         * In case the RampClassLoader is not able to resolve the class it will delegate
         * its resolution to the SystemClassLoader.
         */
        ObjectInputStream in = new ClassLoaderObjectInputStream(RampEntryPoint.getRampClassLoader(), bin);

        deserialized = in.readObject();
        in.close();
        bin.close();
        return deserialized;
    }

    public static GenericPacket deserializePacket(byte[] bytes) throws Exception {
        return E2EComm.deserializePacket(bytes, 0, bytes.length);
    }

    public static GenericPacket deserializePacket(byte[] bytes, int offset, int length) throws Exception {
        GenericPacket res;
        if (RampEntryPoint.protobuf) {
            byte packetId = bytes[offset];
            if (packetId == UnicastHeader.PACKET_ID) {
                res = UnicastHeader.parseFromProtos(bytes, offset + 1, length - 1);
            } else if (packetId == UnicastPacket.PACKET_ID) {
                res = UnicastPacket.parseFromProtos(bytes, offset + 1, length - 1);
            } else if (packetId == BroadcastPacket.PACKET_ID) {
                res = BroadcastPacket.parseFromProtos(bytes, offset + 1, length - 1);
            } else if (packetId == HeartbeatRequest.PACKET_ID) {
                res = HeartbeatRequest.parseFromProtos(bytes, offset + 1, length - 1);
            } else if (packetId == HeartbeatResponse.PACKET_ID) {
                res = HeartbeatResponse.parseFromProtos(bytes, offset + 1, length - 1);
            } else {
                throw new Exception("E2EComm.deserializePacket: unknown packetId " + packetId);
            }
        } else {
            res = (GenericPacket) E2EComm.deserialize(bytes, 0, bytes.length);
        }
        return res;
    }

    //    public static Object readObject(InputStream is) throws Exception {
//    	Object obj;
//		ObjectInputStream in = new ObjectInputStream(is);
//		obj = in.readObject();
//    	return obj;
//    }
    public static GenericPacket readPacket(InputStream is) throws Exception {
        GenericPacket res = null;

        if (RampEntryPoint.protobuf) {
            int read = is.read();
            if (read == -1) {
                System.out.println("E2EComm.deserializePacket: end of stream detected");
                throw new Exception("end of stream detected");
            } else {
                byte packetId = (byte) read;
                if (packetId == UnicastHeader.PACKET_ID) {
                    res = UnicastHeader.parseFromProtos(is);
                } else if (packetId == UnicastPacket.PACKET_ID) {
                    res = UnicastPacket.parseFromProtos(is);
                } else if (packetId == BroadcastPacket.PACKET_ID) {
                    res = BroadcastPacket.parseFromProtos(is);
                } else if (packetId == HeartbeatRequest.PACKET_ID) {
                    res = HeartbeatRequest.parseFromProtos(is);
                } else if (packetId == HeartbeatResponse.PACKET_ID) {
                    res = HeartbeatResponse.parseFromProtos(is);
                } else {
                    throw new Exception("E2EComm.deserializePacket: unknown packet with packetId " + packetId);
                }
            }
        } else {
            ObjectInputStream in = new ObjectInputStream(is);
            return (GenericPacket) in.readObject();
            //return (GenericPacket)E2EComm.readObject(is);
        }

        return res;
    }

//    public static void writeObject(Object obj, OutputStream os) throws Exception{
//    	ObjectOutputStream out = new ObjectOutputStream(os);
//    	out.writeObject(obj);
//    }

    public static void writePacket(GenericPacket gp, OutputStream os) throws Exception {
        if (RampEntryPoint.protobuf) {
            //System.out.println("E2EComm.writeObjectPacket gp.getPacketId()="+gp.getPacketId()+" (int)gp.getPacketId()="+(int)gp.getPacketId());
            os.write(gp.getPacketId());
            gp.writeToProtos(os);
        } else {
            ObjectOutputStream out = new ObjectOutputStream(os);
            out.writeObject(gp);
            //E2EComm.writeObject(gp, os);
        }
    }

}

