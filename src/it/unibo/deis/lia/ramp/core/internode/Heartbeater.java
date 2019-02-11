
package it.unibo.deis.lia.ramp.core.internode;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
//import android.net.wifi.WifiManager.MulticastLock;
import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;

/**
 *
 * @author Carlo Giannelli
 */
public class Heartbeater extends Thread {

	private Hashtable<InetAddress, NeighborData> neighbors = new Hashtable<InetAddress, NeighborData>();
	private HashSet<InetAddress> neighborsBlackList = new HashSet<InetAddress>();

	private int heartbeatPeriod = 60 * 1000; // millis
	private byte[] heartbeatRequestBytes;

	private static Heartbeater heartbeater = null;

	// UPnP/SSDP group, but different port
	public static final String HEARTBEAT_MULTICAST_ADDRESS = "239.255.255.250";

	public static synchronized Heartbeater getInstance(boolean forceStart) {
		if (forceStart && heartbeater == null) {
			heartbeater = new Heartbeater();
			heartbeater.start();
		}
		return heartbeater;
	}

	private Heartbeater() {
		try {
			neighborsBlackList.add(InetAddress.getByName("137.204.56.113")); // desktop Stefano
			neighborsBlackList.add(InetAddress.getByName("137.204.57.31"));  // jacopo
			neighborsBlackList.add(InetAddress.getByName("137.204.57.170")); // relay server
			neighborsBlackList.add(InetAddress.getByName("137.204.57.172")); // portatile carlo, vm studente
			neighborsBlackList.add(InetAddress.getByName("137.204.57.183")); // desktop Carlo
			neighborsBlackList.add(InetAddress.getByName("137.204.57.192")); // Qnap
			neighborsBlackList.add(InetAddress.getByName("137.204.57.155")); // Macchina di prova
			neighborsBlackList.add(InetAddress.getByName("137.204.57.156")); // Macchina di prova
			neighborsBlackList.add(InetAddress.getByName("137.204.57.157")); // Macchina di prova
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}

		HeartbeatRequest hReq = new HeartbeatRequest();

		try {
			// from object to byte[]
			heartbeatRequestBytes = E2EComm.serializePacket(hReq);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean active = true;

	public void stopHeartbeater() {
		System.out.println("Heartbeater STOP");
		active = false;
		interrupt();
	}

	@Override
	public void run() {
		try {
			System.out.println("Heartbeater START");
			while (active) {
				sendHeartbeat(false);
				//sendHeartbeat();
				sleep(heartbeatPeriod);
			}
		} catch (InterruptedException ie) {

		} catch (Exception e) {
			e.printStackTrace();
		}
		heartbeater = null;
		System.out.println("Heartbeater END");
	}

	public void sendHeartbeat(boolean force) {
		//System.out.println("Heartbeater.sendHeartbeat force="+force);
		//System.out.println("Heartbeater.sendHeartbeat");

		Vector<String> localInterfaces = null;
		try {
			localInterfaces = Dispatcher.getLocalNetworkAddresses(force);
		}
		catch (Exception e1) {
			e1.printStackTrace();
		}

		/*if (RampEntryPoint.getAndroidContext() != null) {
			WifiManager wifi = (WifiManager) RampEntryPoint.getAndroidContext().getSystemService(Context.WIFI_SERVICE);
			if (wifi != null) {
				wifiMulticastLock = wifi.createMulticastLock("UdpDispatcher-MulticastLock");
				wifiMulticastLock.acquire();
			}
		}*/

		// multicast
		for (int i = 0; localInterfaces!=null && i < localInterfaces.size(); i++) {
			String anInterface = localInterfaces.elementAt(i);
			//for(int i=0; i<10; i++){
				try{

					MulticastSocket ms = new MulticastSocket();
					ms.setReuseAddress(true);
					ms.setBroadcast(true);
					NetworkInterface netInt = NetworkInterface.getByInetAddress(InetAddress.getByName(anInterface));
					ms.setNetworkInterface(netInt);

					//System.out.println("Heartbeater.sendHeartbeat: sending multicast via "+anInterface);
					// required to send even towards the multicast heartbeat address
					DatagramPacket dp = new DatagramPacket(
							heartbeatRequestBytes,
							heartbeatRequestBytes.length,
							InetAddress.getByName(Heartbeater.HEARTBEAT_MULTICAST_ADDRESS),
							Dispatcher.DISPATCHER_PORT
						);
					//ms.setTimeToLive(1);
					ms.send(dp);
					ms.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				// just wait a bit...
				try {
					sleep(50);
				}
				catch (InterruptedException e) {
					//e.printStackTrace();
				}
			//}
		} // end for

		/*if (RampEntryPoint.getAndroidContext() != null) {
			if (wifiMulticastLock != null && wifiMulticastLock.isHeld())
				wifiMulticastLock.release();
		}*/

		// broadcast
		/**/try {
			//Vector<String> localInterfaces = Dispatcher.getLocalNetworkAddresses(force);
			for (int i = 0; localInterfaces!=null && i < localInterfaces.size(); i++) {
				String anInterface = localInterfaces.elementAt(i);
				// System.out.println("Heartbeater: anInterface "+anInterface);
				try {
					InetAddress inetA = InetAddress.getByName(anInterface);
					NetworkInterface netA = NetworkInterface.getByInetAddress(inetA);
					// System.out.println("Heartbeater sending request via "+netA);

					Set<InetAddress> broadcastAddresses = new HashSet<InetAddress>();
					if (RampEntryPoint.getAndroidContext() != null) {
						WifiManager wifiManager = (WifiManager) RampEntryPoint.getAndroidContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
						// Wi-Fi adapter is ON
						if (wifiManager.isWifiEnabled()) {
							WifiInfo wifiInfo = wifiManager.getConnectionInfo();

							// Connected to an access point
					        if (wifiInfo.getNetworkId() != -1) {
								DhcpInfo dhcp = wifiManager.getDhcpInfo();
								// System.out.println("Heartbeater dhcp.netmask " + dhcp.netmask);
								// int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
								// int broadcast = dhcp.ipAddress | ( ~dhcp.netmask );

								byte[] ipaddressQuads = new byte[4];
								for (int k = 0; k < 4; k++) {
									ipaddressQuads[k] = (byte) ((wifiManager.getConnectionInfo().getIpAddress() >> k * 8) & 0xFF);
									// System.out.println("Heartbeater ipaddressQuads["+k+"] " + ipaddressQuads[k]);
								}
								// System.out.println("Heartbeater InetAddress.getByAddress(ipaddressQuads) " + InetAddress.getByAddress(ipaddressQuads));
								// broadcastAddresses.add(InetAddress.getByAddress(ipaddressQuads));

								byte[] netmaskQuads = new byte[4];
								for (int k = 0; k < 4; k++) {
									netmaskQuads[k] = (byte) ((dhcp.netmask >> k * 8) & 0xFF);
									// System.out.println("Heartbeater netmaskQuads["+k+"] " + netmaskQuads[k]);
								}
								// System.out.println("Heartbeater InetAddress.getByAddress(netmaskQuads) " + InetAddress.getByAddress(netmaskQuads));
								// broadcastAddresses.add(InetAddress.getByAddress(netmaskQuads));

								int broadcast = wifiManager.getConnectionInfo().getIpAddress() | (~dhcp.netmask);
								// System.out.println("Heartbeater broadcast " + broadcast);
								byte[] broadcastQuads = new byte[4];
								for (int k = 0; k < 4; k++) {
									broadcastQuads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
									// System.out.println("Heartbeater broadcast["+k+"] " + broadcastQuads[k]);
								}
								// System.out.println("Heartbeater InetAddress.getByAddress(broadcastQuads) " + InetAddress.getByAddress(broadcastQuads));

								broadcastAddresses.add(InetAddress.getByAddress(broadcastQuads));
					        }
						}

					}
					else {
						// NOT Android
						List<InterfaceAddress> interfaceAddresses = netA.getInterfaceAddresses();
						if (interfaceAddresses != null && interfaceAddresses.size() > 0) {
							// System.out.println(anInterface+": interfaceAddresses.size() = "+interfaceAddresses.size());
							for (int j = 0; j < interfaceAddresses.size(); j++) {
								InterfaceAddress interfaceA = netA.getInterfaceAddresses().get(j);
								// System.out.println("Heartbeater interfaceA " + interfaceA);
								if (interfaceA != null && interfaceA.getBroadcast() != null) {
									// System.out.println("Heartbeater interfaceA.getBroadcast() " + interfaceA.getBroadcast());
									// System.out.println("Heartbeater interfaceA.getNetworkPrefixLength() " + interfaceA.getNetworkPrefixLength());
									broadcastAddresses.add(interfaceA.getBroadcast());
								}
							}
						}
					}

					// System.out.println(anInterface+": broadcast = "+broadcastVector);
					DatagramSocket ds = new DatagramSocket(0, inetA);
					ds.setReuseAddress(true);
					ds.setBroadcast(true);

					// required to send even towards the multicast heartbeat address
					//broadcastAddresses.add(InetAddress.getByName(Heartbeater.HEARTBEAT_MULTICAST_ADDRESS));

					if (anInterface.startsWith("10.")) {
						broadcastAddresses.add(InetAddress.getByName("255.255.255.255"));
					}
					if (broadcastAddresses.size() == 0) {
						broadcastAddresses.add(InetAddress.getByName("255.255.255.255"));
						//broadcastAddresses.add(InetAddress.getByName("192.168.180.49")); broadcastAddresses.add(InetAddress.getByName("192.168.255.255")); broadcastAddresses.add(InetAddress.getByName("192.255.255.255")); broadcastAddresses.add(InetAddress.getByName("192.168.180.255"));
						//broadcastAddresses.add(InetAddress.getByName("192.168.181.255")); broadcastAddresses.add(InetAddress.getByName("192.168.182.255")); broadcastAddresses.add(InetAddress.getByName("192.168.183.255"));
					}

					Iterator<InetAddress> it = broadcastAddresses.iterator();
					while (it.hasNext()) {
						Thread.sleep(50);
						InetAddress broadcastAddress = it.next();
						//System.out.println("Heartbeater: sending from " + inetA + " to " + broadcastAddress);
						DatagramPacket dp = new DatagramPacket(
								heartbeatRequestBytes,
								heartbeatRequestBytes.length,
								broadcastAddress,
								Dispatcher.DISPATCHER_PORT
							);
						try {
							ds.send(dp);
							sleep(50);
						}
						catch (java.net.SocketException se) {
							// System.out.println("Heartbeater ds.send(dp) SocketException to "+broadcastAddress+": "+se.getMessage());
							// se.printStackTrace();
							// System.out.println("Heartbeater: sending to 255.255.255.255 instead of " + broadcastAddress);
							dp = new DatagramPacket(
									heartbeatRequestBytes,
									heartbeatRequestBytes.length,
									InetAddress.getByName("255.255.255.255"),
									Dispatcher.DISPATCHER_PORT
								);
							ds.send(dp);
						}
					}
					ds.close();
					Thread.sleep(100);
				}
				catch (Exception e) {
					System.out.println("Heartbeater Exception from " + anInterface + ": " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.out.println("Heartbeater " + e.getMessage());
			// on debian/ubuntu, remember to change the file
			// "/etc/sysctl.d/bindv6only.conf"
			// from
			// "net.ipv6.bindv6only = 1"
			// to
			// "net.ipv6.bindv6only = 0"
			// and finally invoke
			// "invoke-rc.d procps restart"
		}/**/

		//STEFANO LANZONE: Fare N Unicast
		//implement unicast-based discovery exploiting netmask...
		for (int i = 0; localInterfaces!=null && i < localInterfaces.size(); i++) {
			String anInterface = localInterfaces.elementAt(i);
			try {
				InetAddress inetA = InetAddress.getByName(anInterface);
				NetworkInterface netA = NetworkInterface.getByInetAddress(inetA);

				if (RampEntryPoint.getAndroidContext() != null) {
					WifiManager wifiManager = (WifiManager) RampEntryPoint.getAndroidContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
					// Wi-Fi adapter is ON
					if (wifiManager.isWifiEnabled()) {
						WifiInfo wifiInfo = wifiManager.getConnectionInfo();

						// Connected to an access point
				        if (wifiInfo.getNetworkId() != -1) {
							DhcpInfo dhcp = wifiManager.getDhcpInfo();

							byte[] ipaddressQuads = new byte[4];
							for (int k = 0; k < 4; k++) {
								ipaddressQuads[k] = (byte) ((wifiManager.getConnectionInfo().getIpAddress() >> k * 8) & 0xFF);
							}

							byte[] netmaskQuads = new byte[4];
							for (int k = 0; k < 4; k++) {
								netmaskQuads[k] = (byte) ((dhcp.netmask >> k * 8) & 0xFF);
							}

							InetAddress ipaddress = InetAddress.getByAddress(ipaddressQuads);
							InetAddress netmask = InetAddress.getByAddress(netmaskQuads);

							DatagramSocket ds = new DatagramSocket(0, ipaddress);

							String ip = ipaddress.toString().replaceAll("/", "").split(":")[0];

							SubnetUtils utils = new SubnetUtils(ip, netmask.toString().replaceAll("/", "").split(":")[0]);
							SubnetInfo info = utils.getInfo();

							unicastDiscovery(ipaddress, ds, ip, info);
				        }
					}

//					BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//					// Temporary workaround
//					if (bluetoothAdapter != null) {
//					    // Device support Bluetooth
//						if (bluetoothAdapter.isEnabled()) {
//							System.out.println("---->DENTRO BLUETOOTH");
//
//							DatagramSocket ds = new DatagramSocket(0, inetA);
//
//							String ip = inetA.toString().replaceAll("/", "").split(":")[0];
//							int prefixLength = 24;
//							String subnet = ip + "/" + prefixLength;
//
//							SubnetUtils utils = new SubnetUtils(subnet);
//							SubnetInfo info = utils.getInfo();
//
//							System.out.println("Heartbeater - sendHeartbeat: inetAddress " + inetA);
//							System.out.println("Heartbeater - sendHeartbeat: ip " + ip + ", prefixLength: " + prefixLength);
//							System.out.println("Heartbeater - sendHeartbeat: subnet " + subnet);
//							System.out.println("Heartbeater - sendHeartbeat: info " + info);
//
//							unicastDiscovery(inetA, ds, ip, info);
//						}
//					}

				} else {	// NOT Android
					for (InterfaceAddress address : netA.getInterfaceAddresses()) {
						InetAddress inetAddress = address.getAddress();
						DatagramSocket ds = new DatagramSocket(0, inetAddress);

						String ip = inetAddress.toString().replaceAll("/", "").split(":")[0];
						int prefixLength = address.getNetworkPrefixLength();
						String subnet = ip + "/" + prefixLength;

						SubnetUtils utils = new SubnetUtils(subnet);
						SubnetInfo info = utils.getInfo();

						unicastDiscovery(inetAddress, ds, ip, info);

						Thread.sleep(50);
					}
				}
			}
			catch (Exception e) {
				System.out.println("Heartbeater Exception from " + anInterface + ": " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void unicastDiscovery(InetAddress inetAddress, DatagramSocket ds, String ip, SubnetInfo info) throws UnknownHostException {

		if (info.getAddressCount() < 255) //Impostato limite sul numero di unicast...
		{
			for (String ipDest : info.getAllAddresses())
			{
				if(!ip.equals(ipDest))
				{
					DatagramPacket dp = new DatagramPacket(
					heartbeatRequestBytes,
					heartbeatRequestBytes.length,
					InetAddress.getByName(ipDest),
					Dispatcher.DISPATCHER_PORT
					);

					try {
//						System.out.println("Heartbeater Unicast: sending from " + inetAddress + " to " + ipDest);
						ds.send(dp);
						//sleep(50);
					}
					catch (Exception e) {
						System.out.println("Heartbeater Unicast Error: sending from " + inetAddress + " to " + ipDest);
						e.printStackTrace();
					}
				}
			}
		}
		ds.close();
	}

	// Alessandro Dolci
	private short getNeighborAddressNetworkPrefixLength(InetAddress neighborInetAddress) {
		Vector<String> localAddresses = null;
		try {
			localAddresses = Dispatcher.getLocalNetworkAddresses();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		short neighborAddressNetworkPrefixLength = -1;
		boolean found = false;
		for (int i = 0; i < localAddresses.size() && found == false; i++) {
			String localAddress = localAddresses.elementAt(i);
			InetAddress localInetAddress = null;
			try {
				localInetAddress = InetAddress.getByName(localAddress);
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			}
			NetworkInterface networkInterface = null;
			try {
				networkInterface = NetworkInterface.getByInetAddress(localInetAddress);
			} catch (SocketException e) {
				e.printStackTrace();
			}
			InterfaceAddress localInterfaceAddress = null;
			List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
			for (InterfaceAddress interfaceAddress : interfaceAddresses)
				if (interfaceAddress.getAddress().equals(localInetAddress))
					localInterfaceAddress = interfaceAddress;
			short networkPrefixLength = localInterfaceAddress.getNetworkPrefixLength();
			String completeLocalAddress = localAddress + "/" + networkPrefixLength;
			SubnetUtils subnetUtils = new SubnetUtils(completeLocalAddress);
			SubnetInfo subnetInfo = subnetUtils.getInfo();
			if (subnetInfo.isInRange(neighborInetAddress.getHostAddress())) {
				neighborAddressNetworkPrefixLength = networkPrefixLength;
				found = true;
			}
		}
		return neighborAddressNetworkPrefixLength;
	}

	protected void addNeighbor(InetAddress neighborInetAddress, int nodeId) {
		if(!neighborsBlackList.contains(neighborInetAddress)){
			short neighborAddressNetworkPrefixLength = getNeighborAddressNetworkPrefixLength(neighborInetAddress);
			neighbors.put(
					neighborInetAddress,
					new NeighborData(
							System.currentTimeMillis(),
							nodeId,
							neighborAddressNetworkPrefixLength
							)
					);
		}
	}

	protected boolean isNeighbor(InetAddress neighborInetAddress) {
		return neighbors.containsKey(neighborInetAddress);
	}

	// public synchronized Vector<InetAddress> getNeighbors() throws Exception{
	public Vector<InetAddress> getNeighbors() {
		// System.out.println("Heartbeater.getNeighbors start");
		Vector<InetAddress> res = new Vector<InetAddress>();
		Enumeration<InetAddress> keys = neighbors.keys();
		while (keys.hasMoreElements()) {
			InetAddress address = keys.nextElement();
			//Long lastUpdate = neighbors.get(address);
			NeighborData neighbor = neighbors.get(address);
			if( neighbor != null ){
				Long lastUpdate = neighbor.getLastRefersh();
				if (lastUpdate != null) {
					if (System.currentTimeMillis() - lastUpdate > heartbeatPeriod + (heartbeatPeriod / 2)) {
						neighbors.remove(address);
					}
					else {
						res.addElement(address);
					}
				}
			}
		}
		return res;
	}

	public Integer getNodeId(InetAddress address){
		Integer res = null;
		NeighborData data = this.neighbors.get(address);
		if( data != null ){
			res = data.getNodeId();
		}
		return res;
	}
	
	// Alessandro Dolci
	public short getNetworkPrefixLength(InetAddress address) {
		short res = -1;
		NeighborData data = this.neighbors.get(address);
		if (data != null) {
			res = data.getNetworkPrefixLength();
		}
		return res;
	}

	public static class NeighborData {

		private long lastRefersh;
		private int nodeId;
		private short networkPrefixLength; // Alessandro Dolci

		private NeighborData(long lastRefersh, int nodeId, short networkPrefixLength) {
			super();
			this.lastRefersh = lastRefersh;
			this.nodeId = nodeId;
			this.networkPrefixLength = networkPrefixLength;
		}

		public long getLastRefersh() {
			return lastRefersh;
		}
		public int getNodeId() {
			return nodeId;
		}
		public short getNetworkPrefixLength() {
			return networkPrefixLength;
		}

	}

}
