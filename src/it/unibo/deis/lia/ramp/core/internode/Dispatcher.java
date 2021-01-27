
package it.unibo.deis.lia.ramp.core.internode;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import it.unibo.deis.lia.ramp.RampEntryPoint;

/**
 *
 * @author Carlo Giannelli
 */
public class Dispatcher {

	final static public int DISPATCHER_PORT = 1979;

	// private static Vector<String> localNetworkAddresses = null;
	private static Map<String, Integer> localNetworkAddresses = null;
	private static long lastLocalNetworkAddresses = 0;

	private UdpDispatcher udpDispatcher;
	private TcpDispatcher tcpDispatcher;

	private static Set<String> ignoredLocalInterfaces = new HashSet<String>();

	private Vector<PacketForwardingListener> packetForwardingListeners = new Vector<PacketForwardingListener>();

	private Dispatcher() throws Exception {
		/*
		 * ignoredLocalInterfaces.add("169.254.233.32");
		 * ignoredLocalInterfaces.add("192.168.153.25");
		 * ignoredLocalInterfaces.add("192.168.183.1");
		 * ignoredLocalInterfaces.add("192.168.112.1"); /
		 **/

		// Dispatcher.setLocalNodeId(createRandomId()); // on Android emulator
		Dispatcher.setLocalNodeId(createLocalId());
		// Dispatcher.setLocalNodeId("fakeNodeId_81"); // decommentare la riga
		// sopra per generarlo automaticamente
		Dispatcher.getLocalNetworkAddresses(true);
		udpDispatcher = new UdpDispatcher();
		udpDispatcher.start();
		tcpDispatcher = new TcpDispatcher();
		tcpDispatcher.start();
	}

	private static Dispatcher dispatcher = null;

	public synchronized static Dispatcher getInstance(boolean forceStart) {
		if (forceStart && Dispatcher.dispatcher == null) {
			try {
				Dispatcher.dispatcher = new Dispatcher();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return Dispatcher.dispatcher;
	}

	public void stopDispatcher() {
		System.out.println("Dispatcher.stopDispatcher");
		udpDispatcher.stopUdpDispatcher();
		udpDispatcher = null;
		tcpDispatcher.stopTcpDisptacher();
		tcpDispatcher = null;
		Dispatcher.dispatcher = null;
	}

	public static Vector<String> getLocalNetworkAddresses() throws Exception {
		return getLocalNetworkAddresses(false);
	}

//	 synchronized public static Vector<String> getLocalNetworkAddresses(boolean force) throws Exception {
//		if (System.currentTimeMillis() - lastLocalNetworkAddresses < 1000) {
//			// always wait at least 1000ms
//		} else if (!force && (System.currentTimeMillis() - lastLocalNetworkAddresses < 15000)) {
//			// if force is false, do nothing for 15000ms
//		} else {
//			lastLocalNetworkAddresses = System.currentTimeMillis();
//			Vector<String> newLocalNetworkAddresses = new Vector<String>();
//			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
//				NetworkInterface intf = en.nextElement();
//				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
//					InetAddress inetAddress = enumIpAddr.nextElement();
//					if (!inetAddress.isLoopbackAddress()) {
//						String ip = inetAddress.getHostAddress().toString();
//						if (!ip.contains(":")) { // do not consider IPv6
//													// addresses
//							if (!ignoredLocalInterfaces.contains(ip)) {
//								// System.out.println("Dispatcher.getInternalLocalNetworkAddresses:
//								// adding"+ip);
//								newLocalNetworkAddresses.addElement(ip);
//							} else {
//								// System.out.println("Dispatcher.getInternalLocalNetworkAddresses:
//								// ignoring "+ip);
//							}
//						}
//					}
//				}
//			}
//			localNetworkAddresses = newLocalNetworkAddresses;
//		}
//		return localNetworkAddresses;
//	}

	synchronized public static Vector<String> getLocalNetworkAddresses(boolean force) throws Exception {
		if (System.currentTimeMillis() - lastLocalNetworkAddresses < 1000) {
			// always wait at least 1000ms
		} else if (!force && (System.currentTimeMillis() - lastLocalNetworkAddresses < 15000)) {
			// if force is false, do nothing for 15000ms
		} else {
			lastLocalNetworkAddresses = System.currentTimeMillis();
			Map<String, Integer> newLocalNetworkAddresses = new HashMap<String, Integer>();
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (InterfaceAddress ia : intf.getInterfaceAddresses()) {
					InetAddress inetAddress = ia.getAddress();
					int netmaskLength = ia.getNetworkPrefixLength();
					// for (Enumeration<InetAddress> enumIpAddr =
					// intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					// InetAddress inetAddress = enumIpAddr.nextElement();
					// NetworkInterface ni =
					// NetworkInterface.getByInetAddress(inetAddress);
					// int netmaskLength =
					// /*ni.getInterfaceAddresses().get(0).getNetworkPrefixLength();*/24;
					// // FIXME sul portatile non restituisce un
					// InterfaceAddress (problemi di permessi??)
					if (!inetAddress.isLoopbackAddress()) {
						String ip = inetAddress.getHostAddress().toString();
						if (!ip.contains(":")) { // do not consider IPv6
													// addresses
							if (!ignoredLocalInterfaces.contains(ip)) {
								// System.out.println("Dispatcher.getInternalLocalNetworkAddresses:
								// adding "+ip);
								newLocalNetworkAddresses.put(ip, netmaskLength);
							} else {
								// System.out.println("Dispatcher.getInternalLocalNetworkAddresses:
								// ignoring "+ip);
							}
						}
					}
				}
			}
			localNetworkAddresses = newLocalNetworkAddresses;
		}
		return new Vector<String>(localNetworkAddresses.keySet());
	}

	synchronized public static int getNetmaskLength(String ip) {
		return localNetworkAddresses.get(ip);
	}

	// ---------------------------------
	// localId, both string and integer
	// ---------------------------------
	private static int localRampId = "".hashCode();
	private static String localRampIdString = "";

	public static int getLocalRampId() {
		return localRampId;
	}

	public static String getLocalRampIdString() {
		return localRampIdString;
	}

	private static void setLocalNodeId(String newLocalNodeIdString) {
		if (newLocalNodeIdString == null || newLocalNodeIdString.equals("")) {
			Dispatcher.localRampId = "".hashCode(); // null;
			Dispatcher.localRampIdString = null;
		} else {
			// Dispatcher.localRampId = newLocalNodeIdString.hashCode();
			Dispatcher.localRampId = Integer.parseInt(newLocalNodeIdString);
			Dispatcher.localRampIdString = newLocalNodeIdString;
		}
		System.out.println("Dispatcher: localRampId=" + localRampId + " localRampIdString=" + localRampIdString);
	}

	private String createLocalId() {
		String nodeId = RampEntryPoint.getRampProperty("nodeID");
		if (nodeId == null) {
			StringBuilder tmpNodeId = new StringBuilder("" + RampEntryPoint.nextRandomInt());
			nodeId = tmpNodeId.toString();
			RampEntryPoint.setRampProperty("nodeID", nodeId);
		}

		return nodeId.toString();
	}

	private String createRandomId() {
		// Random r = new Random();
		float number = RampEntryPoint.nextRandomFloat();
		// nodeId = "fakeNodeId_" + Math.round(number * 1000);
		StringBuilder nodeId = new StringBuilder("fakeNodeId_" + Math.round(number * 1000));
		return nodeId.toString();
	}

	// ------------------------------
	// register/remove/get packet listeners
	// ------------------------------
	public void addPacketForwardingListener(PacketForwardingListener pfw) {
		if (!packetForwardingListeners.contains(pfw)) {
			System.out.println("Dispatcher registering listener: " + pfw.getClass());
			packetForwardingListeners.addElement(pfw);
		}
	}

	/**
	 * @author Dmitrij David Padalino Montenero
	 */
	public void addPacketForwardingListenerBeforeAnother(PacketForwardingListener pfw, PacketForwardingListener referencePfw) {
		if(!packetForwardingListeners.contains(referencePfw)) {
			addPacketForwardingListener(pfw);
		} else {
			int referencePwfIndex = packetForwardingListeners.indexOf(referencePfw);
			packetForwardingListeners.insertElementAt(pfw, referencePwfIndex);
		}
	}

	public void removePacketForwardingListener(PacketForwardingListener pfw) {
		System.out.println("Dispatcher removing listener: " + pfw.getClass());
		packetForwardingListeners.remove(pfw);
	}

	PacketForwardingListener[] getPacketForwardingListeners() {
		PacketForwardingListener[] resArray = new PacketForwardingListener[packetForwardingListeners.size()];
		// return (PacketForwardingListener[])
		// (packetForwardingListeners.toArray(resArray));
		return packetForwardingListeners.toArray(resArray);
	}

	// ------------------------------
	// ERN related stuff
	// ------------------------------
	public static boolean sendToRin(String destIp) {
		try {

			// If not a neighbor or localhost is a RIN
			if (destIp.startsWith("127.0."))
				return false;
			boolean isNeighbor = Heartbeater.getInstance(false).isNeighbor(InetAddress.getByName(destIp));
			return !isNeighbor && !isLocalHost(destIp);

		} catch (Exception ex) {

			boolean sendToRin = true;
//			if( ! RampInternetNode.isActive() ){
//				 sendToRin = false;
//			}STEFANO LANZONE
			if (destIp.startsWith("127.0.") || destIp.startsWith("192.168.") || destIp.startsWith("10.")
					|| destIp.startsWith("169.254.")) {
				sendToRin = false;
			} else {
				Vector<String> localNetworkAddresses = null;
				try {
					localNetworkAddresses = Dispatcher.getLocalNetworkAddresses(false);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (localNetworkAddresses != null) {
					for (int i = 0; sendToRin == true && i < localNetworkAddresses.size(); i++) {
						String localNetworkAddressString = localNetworkAddresses.elementAt(i).replaceAll("/", "");
						String[] loc = localNetworkAddressString.split("[.]");
						String[] destOctets = destIp.split("[.]");
						if ( // !firstHop &&
						destOctets[0].equals(loc[0]) && destOctets[1].equals(loc[1]) && destOctets[2].equals(loc[2])) {
							sendToRin = false;
						}
					}
				}
			}
			return sendToRin;

		}
	}

	public static boolean isFromRin(String remoteAddressString) {
		try {

			// If not a neighbor or localhost is a RIN
			if (remoteAddressString.startsWith("127.0."))
				return false;
			boolean isNeighbor = Heartbeater.getInstance(false).isNeighbor(InetAddress.getByName(remoteAddressString));
			return !isNeighbor && !isLocalHost(remoteAddressString);

		} catch (Exception ex) {

			boolean isFromRin = true;
//			if( ! RampInternetNode.isActive() ){
//				isFromRin = false;
//			} STEFANO LANZONE
			if (remoteAddressString.startsWith("127.0.") || remoteAddressString.startsWith("192.168.")
					|| remoteAddressString.startsWith("10.") || remoteAddressString.startsWith("169.254.")) {
				isFromRin = false;
			} else {
				Vector<String> localNetworkAddresses = null;
				try {
					localNetworkAddresses = Dispatcher.getLocalNetworkAddresses(false);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (localNetworkAddresses != null) {
					for (int i = 0; isFromRin == true && i < localNetworkAddresses.size(); i++) {
						String localNetworkAddressString = localNetworkAddresses.elementAt(i).replaceAll("/", "");
						String[] loc = localNetworkAddressString.split("[.]");
						String[] remOctets = remoteAddressString.split("[.]");
//						System.out.println("Remoto:" + remoteAddressString + " Locale:" + localNetworkAddressString+" "+i);
						if (remOctets[0].equals(loc[0]) && remOctets[1].equals(loc[1]) && remOctets[2].equals(loc[2])) {
							System.out.println("Dispatcher.isFromRin: " + i + " remoteAddressString="
									+ remoteAddressString + " localNetworkAddressString=" + localNetworkAddressString);
							isFromRin = false;
						} else if ( // !firstHop &&
						!(remOctets[0].equals(loc[0]) && remOctets[1].equals(loc[1]))) {
							isFromRin = false;
						}
					}
				}
			}
			return isFromRin;
		}
	}

	private static boolean isLocalHost(String ip) {
		Vector<String> localNetworkAddresses = null;
		try {
			localNetworkAddresses = Dispatcher.getLocalNetworkAddresses(false);
			for (String localNetworkAddress : localNetworkAddresses) {
				if (ip.equals(localNetworkAddress))
					return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
