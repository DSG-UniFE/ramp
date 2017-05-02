package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.RampWebServer;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
//import it.unibo.deis.lia.ramp.core.social.SocialObserver;
//import it.unibo.deis.lia.ramp.core.social.SocialObserverTeamlife.TeamlifePostType;
import it.unibo.deis.lia.ramp.service.application.ResourceProvider.HttpProxyServerMonitoredWebServers;
import it.unibo.deis.lia.ramp.service.application.ResourceProvider.MonitoredWebServer;
import it.unibo.deis.lia.ramp.service.application.ResourceProvider.SharedDirectory;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
//import it.unibo.deis.lia.upnp.controlpoint.BasicControlPoint;
//import it.unibo.deis.lia.upnp.controlpoint.ContentDirectoryControlPoint;
//import it.unibo.deis.lia.upnp.controlpoint.ContentDirectoryControlPoint.UPnPObject;
//import it.unibo.deis.lia.upnp.controlpoint.ContentDirectoryControlPoint.UPnPObject.UPnPObjectType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;

public class ResourceDiscovery {
	
	private static boolean active = false;
	
	private Hashtable<Integer, ServiceResponse> availableHttpProxyServers; // HttpProxyServerNodeId, ServiceResponse
	private Hashtable<Integer, Set<MonitoredWebServer>> monitoredWebServers; // HttpProxyServerNodeId, MonitoredWebServers
	private Hashtable<Integer, Set<SharedDirectory>> sharedDirectories; // WebServerId, SharedDirectories
	private int httpProxyClientLocalPort;
	
	private static ResourceDiscovery resourceDiscovery = null;
	
	public static synchronized ResourceDiscovery getInstance(){
		if(resourceDiscovery == null){
			active = true;
			resourceDiscovery = new ResourceDiscovery();
		}
		return resourceDiscovery;
	}
	
	private ResourceDiscovery(){
		System.out.println("ResourceDiscovery.activate");
		availableHttpProxyServers = new Hashtable<Integer, ServiceResponse>();
		monitoredWebServers = new Hashtable<Integer, Set<MonitoredWebServer>>();
		sharedDirectories = new Hashtable<Integer, Set<SharedDirectory>>();
		monitoredDirectories = Collections.synchronizedSet(new HashSet<MonitoredDirectory>());
//		monitoredUPnPContainers = Collections.synchronizedSet(new HashSet<MonitoredUPnPContainer>());
		HttpProxyClient temp = null;
		if(!HttpProxyClient.isActive())
			temp = HttpProxyClient.getInstance();
		synchronized (temp) {
			try {
				while(temp.getListeningPort() == -1)
					temp.wait();
				httpProxyClientLocalPort = temp.getListeningPort();
			} catch (InterruptedException e) {}
		}
		if (RampEntryPoint.getAndroidContext() == null) {
//			startUPnPContainerMonitor();
		}
	}
	
    public static boolean isActive(){
    	return active;
    }

	public static synchronized void deactivate(){
		if(resourceDiscovery != null){
			System.out.println("ResourceDiscovery.deactivate");
			active = false;
			if(HttpProxyClient.isActive())
				HttpProxyClient.getInstance().stopClient();
			resourceDiscovery.stopDirectoryMonitor();
			if (RampEntryPoint.getAndroidContext() == null) {
//				resourceDiscovery.stopUPnPContainerMonitor();
			}
			resourceDiscovery = null;
		}
	}
	
	public synchronized boolean findHttpProxyServerService(int ttl, int timeout, int serviceAmount){
		long pre = System.currentTimeMillis();
		Vector<ServiceResponse> retrievedServices = null;
		try {
			retrievedServices = ServiceDiscovery.findServices(
					ttl,
					"HttpProxyServer",
					timeout,
					serviceAmount,
					null
					);
			long post = System.currentTimeMillis();
			float elapsed = (post-pre)/(float)1000;
			System.out.println("ResourceDiscovery findHttpProxyServerService elapsed=" + elapsed + " services=" + retrievedServices);
			availableHttpProxyServers.clear();
			monitoredWebServers.clear();
			sharedDirectories.clear();
			// TODO different paths to the same proxy server are overwritten
			// only the last one is kept (usually the longest one, because of more RTT)
			for (ServiceResponse serviceResponse : retrievedServices) {
				availableHttpProxyServers.put(serviceResponse.getServerNodeId(), serviceResponse);
			}
			return true;
		} catch (Exception e) {
		}
		return false;
	}
	
	public synchronized Collection<ServiceResponse> getHttpProxyServerServices(){
		return availableHttpProxyServers.values();
	}
	
	public synchronized boolean isLocalHttpProxy(int httpProxyServerId) {
		ServiceResponse sr = availableHttpProxyServers.get(httpProxyServerId);
		if(sr == null) return false;
		// if all the addresses in the path are local to this node return true
		// false otherwise
		String[] path = sr.getServerDest();
		return isLocalPath(path);
	}

	public boolean isLocalPath(String[] path) {
		try {
			Vector<String> localAddresses = Dispatcher.getLocalNetworkAddresses();
			for (int i = 0; i < path.length; i++) { // foreach hop in the path
				String ithHop = path[i];
				// if loopback
				if(ithHop.equals("127.0.0.1") || ithHop.equals("127.0.1.1"))
					continue;
				// if local network address
				if(ithHop.startsWith("10.") || ithHop.startsWith("192.168") || ithHop.startsWith("169.254"))
					continue;
				// if local public address
				String localPublicIp = GeneralUtils.getMyPublicIpString();
				if(ithHop.equals(localPublicIp))
					continue;
				// if local interface address
				boolean equalToLocalAddress = false;
				for (String localAddress : localAddresses) {
					if(ithHop.equals(localAddress)){
						equalToLocalAddress = true;
						break;
					}
				}
				if(equalToLocalAddress)
					continue;
				
				// if same local network // TODO this works only with 24 bit netmask (255.255.255.0)
//				String[] locPublOctects = localPublicIp.split("[.]");
//				String[] ithPathOctects = ithHop.split("[.]");
//				locPublOctects[3] = "0";
//				ithPathOctects[3] = "0";
//				if(Arrays.equals(locPublOctects, ithPathOctects))
//					continue;
				int netmaskLength = Dispatcher.getNetmaskLength(localPublicIp);
				if(isSameSubnet(localPublicIp, ithHop, netmaskLength))
					continue;
				// none of the conditions above
				// it means that the proxy server is in another "island"
				return false;
			} // foreach
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}
	
	private int getContentTypeByPathIndex(int pathIndex){
		switch (pathIndex) {
		case 0:
			return SharedDirectory.MUSIC_TYPE;
		case 1:
			return SharedDirectory.PHOTO_TYPE;
		case 2:
			return SharedDirectory.VIDEO_TYPE;
		default:
			return -1;
		}
	}
	
	public boolean findMonitoredWebServers(int httpProxyServerId){
		final ServiceResponse httpProxyServer = availableHttpProxyServers.get(httpProxyServerId);
		if(httpProxyServer == null){
			System.out.println("ResourceDiscovery findMonitoredWebServers: httpProxyServer == null");
			return false;
		}
		BoundReceiveSocket socket1 = null;
		try {
			socket1 = E2EComm.bindPreReceive(E2EComm.TCP);
			// prepare the request
			HttpProxyServerMonitoredWebServers req = new HttpProxyServerMonitoredWebServers(socket1.getLocalPort(), E2EComm.TCP);
			
			// send the request
			E2EComm.sendUnicast(httpProxyServer.getServerDest(), 
					httpProxyServer.getServerPort(), 
					httpProxyServer.getProtocol(), 
					E2EComm.serialize(req));
			
			// receive the response
			// TODO tune timeout
			GenericPacket response1 = E2EComm.receive(socket1, 10*1000);
			
			if(response1 instanceof UnicastPacket){
				UnicastPacket up1 = (UnicastPacket) response1;
				Object payload = E2EComm.deserialize(up1.getBytePayload());
				
				if(payload instanceof HttpProxyServerMonitoredWebServers){
					//handle the response
					HttpProxyServerMonitoredWebServers responsePayload = (HttpProxyServerMonitoredWebServers) payload;
					Set<MonitoredWebServer> monitoredWebServersFound = responsePayload.getMonitoredWebServers();
					Set<MonitoredWebServer> temp = monitoredWebServers.get(httpProxyServerId);
					
					if(temp == null){
						// initialize and add a new entry
						temp = new HashSet<MonitoredWebServer>();
						monitoredWebServers.put(httpProxyServerId, temp);
					}else{
						temp.clear();
					}
					
					temp.addAll(monitoredWebServersFound);
					final Collection<Thread> workingThreads1 = new ArrayList<Thread>();
					final Collection<Thread> workingThreads2 = new ArrayList<Thread>();
					
					for (final MonitoredWebServer monitoredWebServer : monitoredWebServersFound) { // for each known webserver
						// get shared directories through twonky web server
						
						// gestione server in parallello
						Thread thread1 = new Thread() {
				            @Override public void run() {
				            	
								final int webServerId = httpProxyServer.getServerNodeId() + monitoredWebServer.hashCode();
								final String webServerAddr = monitoredWebServer.getIpAddr();
								final int webServerPort = monitoredWebServer.getPort();
								final String[] paths = monitoredWebServer.getPaths();
								
								// either clear already known shared directories or initialize a new entry 
								Set<SharedDirectory> webServerSharedDirectories = sharedDirectories.get(webServerId);
								if(webServerSharedDirectories == null){
									sharedDirectories.put(webServerId, new HashSet<SharedDirectory>());
								}else{
									webServerSharedDirectories.clear();
								}
								
								System.out.println("ResourceDiscovery findMonitoredWebServers: getting shared directories for Web Server: " + webServerId);
								int pathIndex = 0;
								for (final String twonkySharedPath : paths) { // for each shared path
									
									final int tempPathIndex = pathIndex;
									
									// invio richieste in parallello
									Thread thread2 = new Thread() {
							            @Override public void run() {
							            
											BoundReceiveSocket socket2 = null;
											try {
												socket2 = E2EComm.bindPreReceive(E2EComm.TCP);
												byte[] httpRequest = createHttpRequest(webServerAddr, webServerPort, twonkySharedPath);
												InternetRequest request = new InternetRequest(monitoredWebServer.getIpAddr(), 
														monitoredWebServer.getPort(), 
														socket2.getLocalPort(), 
														E2EComm.TCP, 
														httpRequest);
				
												// send the request through the http proxy server
												E2EComm.sendUnicast(httpProxyServer.getServerDest(), 
														httpProxyServer.getServerPort(), 
														httpProxyServer.getProtocol(), 
														E2EComm.serialize(request));
				
												// receive response
												GenericPacket response2 = E2EComm.receive(socket2, 5*1000); // TODO tune timeout
												if(response2 instanceof UnicastPacket){
													UnicastPacket up2 = (UnicastPacket) response2;
													// parse response and fill sharedDirectories accordingly
													parseTwonkyServerResponse(up2.getBytePayload(), webServerId, tempPathIndex);
												}else{
													System.out.println("ResourceDiscovery findMonitoredWebServers: getting shared directories for Web Server: " + 
															webServerId + " path=" + twonkySharedPath + " payload not an UnicastPacket");
												}
											} catch (Exception e) { 
												//break; // continue with next webserver 
											}
											finally{
												try {
													socket2.close();
												} catch (IOException e) {}
											}
											
							            }
							        };
							        workingThreads2.add(thread2);
									thread2.start();	// end new thread	
									
							        pathIndex++;
							        
								} // for
								
				            }
				        };
				        workingThreads1.add(thread1);
				        thread1.start();	// end new thread	
					
					} // for
					
					for (Thread thread : workingThreads1) {
						thread.join();
					}
					for (Thread thread : workingThreads2) {
						thread.join();
					}
					
					return true;
					
				}else{
					System.out.println("ResourceDiscovery findMonitoredWebServers: payload not an HttpProxyServerMonitoredWebServers");
				}
			}else{
				System.out.println("ResourceDiscovery findMonitoredWebServers: response not an UnicastPacket");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				socket1.close();
			} catch (IOException e) {}
		}
		return false;
	}
	
	public ServiceResponse getProxyServerSameSubnet(String mediaServerIp) {
		if(isLocalMediaServer(mediaServerIp)) {
			for(ServiceResponse proxy : availableHttpProxyServers.values()) {
				if(isLocalHttpProxy(proxy.getServerNodeId()))
					return proxy;
			}
		}
		
//		for(ServiceResponse proxy : availableHttpProxyServers.values()) {
//			if(isProxyServerSameSubnet(proxy.getServerNodeId(), mediaServerIp))
//				return proxy;
//		}
		// no http proxy server in the same subnet of the media server
		return null;
	}
	
	public ServiceResponse getProxyServer(int proxyNodeId) {
		return availableHttpProxyServers.get(proxyNodeId);
	}
	
	public boolean isProxyServerSameSubnet(int proxyNodeId, String mediaServerIp) {
		ServiceResponse proxy = availableHttpProxyServers.get(proxyNodeId);
		if(proxy != null) {
			String address = proxy.getServerDest()[proxy.getServerDest().length - 1];
			if(address != null && !address.isEmpty())
				return isSameSubnet(address, mediaServerIp, 24);
		}
		return false;
	}
	
	private boolean isSameSubnet(String ip1, String ip2, int netmaskLength) {
		String splittedIp1[] = ip1.split("[.]");
		String splittedIp2[] = ip2.split("[.]");
		
		switch(netmaskLength) {
			case 8:
				splittedIp1[1] = "0";
				splittedIp2[1] = "0";
			case 16:
				splittedIp1[2] = "0";
				splittedIp2[2] = "0";
			case 24:
				splittedIp1[3] = "0";
				splittedIp2[3] = "0";
				break;
			default:
				return false;
		}
		
		// if the ip1 is in the same subnet of the ip2
		if(Arrays.equals(splittedIp1, splittedIp2))
			return true;
		return false;
	}
	
	/* 
	 * FIXME In realt� bisognerebbe cercare di recuperare tutto il path verso il server perch� il suo indirizzo 
	 * potrebbe essere locale (es. 192.169.56.101) ma appartenere alla sottorete di un amico Facebook federata con la mia.
	 * Qu� controllo soltanto se l'indirizzo finale del server appartiene o meno ad una mia sottorete.
	 * Ad es. se il server � su 192.168.56.101 di Alice e Bob ha una sottorete 192.168.56.x, il server appare come locale
	 * a Bob perch� il suo indirizzo fa match con la sottorete di Bob. Inoltre i messaggi soap possono passano per i vari 
	 * proxy UPnP che ne modificano gli indirizzi, spacciandosi cos� per i server UPnP: il Control Point avr� in mano 
	 * l'indirizzo IP del proxy UPnP a lui locale, e non il vero IP del server.
	 * XXX Possibile soluzione: inserire un header custom nella risposta SSDP al discovery per marcare il passaggio dal 
	 * proxy UPnP (e quindi sottolineare il fatto che il media server � in un'altra sottorete).
	 * Altra soluzione (quella adottata): guardare se esiste un proxy UPnP che rappresenta il server in esame: se esiste 
	 * il server � remoto, altrimenti � locale.
	 */ 
	public boolean isLocalMediaServer(String mediaServerIp) {
		try {
			for(String iface : Dispatcher.getLocalNetworkAddresses()) {
				if(isSameSubnet(iface, mediaServerIp, Dispatcher.getNetmaskLength(iface)))
					return true;
			}
		} catch (Exception e) {}
		
		return false;
	}
	
	public Collection<SharedDirectory> getSharedDirectoriesByType(int webServerId, int dirType){
		Set<SharedDirectory> temp = sharedDirectories.get(webServerId);
		Set<SharedDirectory> result = new HashSet<SharedDirectory>();
		if(temp == null) return Collections.emptySet();
		for (SharedDirectory sharedDirectory : temp) {
			if(sharedDirectory.getType() == dirType)
				result.add(sharedDirectory);
		}
		return result;
	}
	
	private byte[] createHttpRequest(String webServerAddr, int webServerPort, String path){
		StringBuilder httpRequestBuilder = new StringBuilder(); 
		httpRequestBuilder.append("GET " + path + " HTTP/1.1" + (char)0x0D + (char)0x0A);
		httpRequestBuilder.append("Host: " + webServerAddr + ":" + webServerPort + (char)0x0D + (char)0x0A);
		httpRequestBuilder.append("" + (char)0x0D + (char)0x0A);
		return httpRequestBuilder.toString().getBytes();
	}
	
	synchronized private void parseTwonkyServerResponse(byte[] httpResponseBytes, int webServerId, int pathIndex) {
		System.out.println("ResourceDiscovery parseTwonkyServerResponse: START webServerId = "+webServerId+", pathIndex = "+pathIndex);
		try {
			// The idea here is to parse html returned by twonky in
			// order to find out which directories have been shared.
			// The main issue is that html returned is not a well-formed XML 
			// document, so it necessary to clean it up before parsing it
			TagNode root = parseHtml(httpResponseBytes);
			
			// evaluate XPath to get anchor tags
			// anchors include every <a class="playcontainer"> node
			Object[] anchors = root.evaluateXPath("//a[@class='playcontainer']");

			String dirName = null;
			String dirUrl = null;
			
			Set<SharedDirectory> temp = sharedDirectories.get(webServerId);
			
			int contentType = getContentTypeByPathIndex(pathIndex);
			for (int i = 0; i < anchors.length; i++) { // foreach <a class="playcontainer"> node
				if(anchors[i] instanceof TagNode){
					TagNode anchor = (TagNode) anchors[i];
					dirName = anchor.getText().toString();
					dirUrl = anchor.getAttributeByName("href"); // dirUrl is local to the proxyServer
					if(!dirUrl.startsWith("http://"))
						dirUrl = "http://" + dirUrl;
					temp.add(new SharedDirectory(dirUrl, dirName, contentType));
				}
			} // for
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XPatherException e) {
			e.printStackTrace();
		} 
		System.out.println("ResourceDiscovery parseTwonkyServerResponse: END webServerId = "+webServerId+", pathIndex = "+pathIndex);
	}
	
	private TagNode parseHtml(byte[] httpResponseBytes) throws IOException{
		ByteArrayInputStream bis = new ByteArrayInputStream(httpResponseBytes);
		// clean the answer and return a well-formed XML document
		HtmlCleaner cleaner = new HtmlCleaner();
		CleanerProperties props = cleaner.getProperties();

		// set some properties to non-default values
		props.setTranslateSpecialEntities(true);
		props.setTransResCharsToNCR(true);
		props.setOmitComments(true);

		// do parsing
		TagNode root = cleaner.clean(bis); // bis contains also http headers but the
		bis.close();					   // cleaner does not care and ignores them
										   // root is the <html> node
		return root;
	}

	public Collection<MonitoredWebServer> getMonitoredWebServers(int httpProxyServerId){
		ServiceResponse httpProxyServer = availableHttpProxyServers.get(httpProxyServerId);
		if(httpProxyServer == null){
			System.out.println("ResourceDiscovery getMonitoredWebServers: httpProxyServer == null");
			return null;
		}
		Set<MonitoredWebServer> result = monitoredWebServers.get(httpProxyServerId);
		if(result == null) result = Collections.emptySet();
		return result;
	}
	
	public String createRAMPUrl(int httpProxyServerNodeId, String resourceUrl, boolean globalScope, String whoIsAskingIpAddress){
		// RAMP HTTP URL pattern: 
		// http://ipHttpProxyClient:port/ramp://httpProxyServerNodeId:port:protocol/http://ipWebServer:port/path/to/resource
		ServiceResponse sr = availableHttpProxyServers.get(httpProxyServerNodeId);
		if(sr == null) return "#";

		if(globalScope){
			// return a public address of the proxy client that is reachable from remote
			return "http://" + GeneralUtils.getMyPublicIpString() + ":" + httpProxyClientLocalPort + "/" +
					"ramp://" + httpProxyServerNodeId + ":" + sr.getServerPort() + ":" + sr.getProtocol() + "/" + resourceUrl;
		}else{
			String localScopeAddress = null;
			// return a private address of the proxy client that is reachable from the same IP subnet of the
			// client (e.g. requesting browser) node
			if(whoIsAskingIpAddress.equals("localhost") 
				|| whoIsAskingIpAddress.equals("127.0.0.1")
				|| whoIsAskingIpAddress.equals("127.0.1.1")){
				localScopeAddress = whoIsAskingIpAddress;
			}else{
				try {
					Vector<String> localAddresses = Dispatcher.getLocalNetworkAddresses();
					// TODO this works only with 24 bit netmask (255.255.255.0) 
					String[] whoIsAskingIpAddressOctects = whoIsAskingIpAddress.split("[.]");
					whoIsAskingIpAddressOctects[3] = "0";
					for (String localAddress : localAddresses) {
						String[] localAddressOctects = localAddress.split("[.]");
						localAddressOctects[3] = "0";
						if(Arrays.equals(localAddressOctects, whoIsAskingIpAddressOctects)){
							localScopeAddress = localAddress;
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if(localScopeAddress == null) // could not find a valid local ip address
				localScopeAddress = GeneralUtils.getMyPublicIpString(); // maybe accessing using the external ip address of the Web server

			return 	"http://" + localScopeAddress + ":" + httpProxyClientLocalPort + "/" +
					"ramp://" + httpProxyServerNodeId + ":" + sr.getServerPort() + ":" + sr.getProtocol() + "/" + resourceUrl;
		}
	}
	
	// -------------------- 
	// Directory Monitoring
	// --------------------
	
	private Set<MonitoredDirectory> monitoredDirectories;
//	private Set<MonitoredUPnPContainer> monitoredUPnPContainers;
	private DirectoryMonitor directoryMonitor;
//	private UPnPContainerMonitor upnpContainerMonitor;
	
	public void addMonitoredDirectory(int httpProxyServerNodeId, String url, int type){
		monitoredDirectories.add(new MonitoredDirectory(httpProxyServerNodeId, url, type));
	}
	
//	public boolean addMonitoredUPnPContainer(int httpProxyServerNodeId, String deviceUuid, String objectId){
//		MonitoredUPnPContainer c = new MonitoredUPnPContainer(deviceUuid, objectId, httpProxyServerNodeId);
//		c = buildParentsPath(c);
//		synchronized (monitoredUPnPContainers) {
//			return monitoredUPnPContainers.add(c);
//		}
//	}
	
	// TODO con la modifica al monitoring per uShare (inserimento del path completo dalla root alla directory da monitorare), il metodo remove � ancora da testare
//	public boolean removeMonitoredUPnPContainer(int httpProxyServerNodeId, String deviceUuid, String objectId){
//		MonitoredUPnPContainer c = new MonitoredUPnPContainer(deviceUuid, objectId, httpProxyServerNodeId);
//		c = buildParentsPath(c);
//		synchronized (monitoredUPnPContainers) {
//			return monitoredUPnPContainers.remove(c);
//		}
//	}
	
//	public boolean isMonitoredUPnPContainer(int httpProxyServerNodeId, String deviceUuid, String objectId, Stack<UPnPObject> path, UPnPObject actualObj){
//		MonitoredUPnPContainer c = new MonitoredUPnPContainer(deviceUuid, objectId, httpProxyServerNodeId);
////		c = buildParentsPath(c);
//		path.add(0, actualObj);
//		c.setParentsPath(path);
//			
//		synchronized (monitoredUPnPContainers) {
//			for (MonitoredUPnPContainer monitoredContainer : monitoredUPnPContainers) {
//				if(monitoredContainer.equals(c))
//					return true;
//			}
//			return false;
//		}
//	}
	
	public boolean isMonitoredDirectory(int httpProxyServerNodeId, String url, int type){
		synchronized (monitoredDirectories) {
			for (MonitoredDirectory monitoredDirectory : monitoredDirectories) {
				if(monitoredDirectory.httpProxyServerNodeId == httpProxyServerNodeId &&
						monitoredDirectory.url.equals(url) &&
						monitoredDirectory.type == type)
					return true;
			}
			return false;
		}
	}
	
//	private MonitoredUPnPContainer buildParentsPath(MonitoredUPnPContainer container) {
////		ContentDirectoryControlPoint cdcp = (ContentDirectoryControlPoint) BasicControlPoint.getInstance(RampEntryPoint.getRampProperty("contentDirectoryCP"));
////		Device device = cdcp.getDevice(container.getDeviceUuid());
////		// we have to retrieve the full path (see getActualUPnPContainerId() notes)
////		UPnPObject obj = cdcp.browseParents((RemoteDevice) device, container.getObjectId());
////		container.setParentsPath(cdcp.revertPath(obj));
//		return container;
//	}
	
	public int numberMonitoredDirectories(){
		synchronized (monitoredDirectories) {
			int num = monitoredDirectories.size();
			return num;
		}
	}
	
//	public int numberMonitoredUPnPContainers(){
//		synchronized (monitoredUPnPContainers) {
//			int num = monitoredUPnPContainers.size();
//			return num;
//		}
//	}
	
	public void startDirectoryMonitor(){
		if(directoryMonitor == null){
			directoryMonitor = new DirectoryMonitor();
			directoryMonitor.start();
		}
	}
	
//	public void startUPnPContainerMonitor(){
//		if(upnpContainerMonitor == null){
//			upnpContainerMonitor = new UPnPContainerMonitor();
//			upnpContainerMonitor.start();
//		}
//	}
	
	public void stopDirectoryMonitor(){
		if(directoryMonitor != null && directoryMonitor.isAlive()){
			monitoredDirectories.clear();
			directoryMonitor.interrupt();
			directoryMonitor = null;
		}
	}
	
//	public void stopUPnPContainerMonitor(){
//		if(upnpContainerMonitor != null && upnpContainerMonitor.isAlive()){
//			monitoredUPnPContainers.clear();
//			upnpContainerMonitor.interrupt();
//			upnpContainerMonitor = null;
//		}
//	}
	
//	public boolean isUPnPContainerMonitorAlive() {
//		return upnpContainerMonitor != null && upnpContainerMonitor.isAlive();
//	}
	
	private class DirectoryMonitor extends Thread {
		
		 // XXX period aggressivo, poi lo rimettiamo a valori piu' consoni
		private static final int MONITORING_PERIOD = 15 * 1000; //1 * 60 * 1000;
		private BoundReceiveSocket socket;
		
		@Override
		public void run() {
			
			System.out.println("ResourceDiscovery.DirectoryMonitor: START");
			
			try {
				socket = E2EComm.bindPreReceive(E2EComm.TCP);
			} catch (Exception e1) {
//				e1.printStackTrace();
				System.out.println("ResourceDiscovery.DirectoryMonitor: Cannot bind socket");
				System.out.println("ResourceDiscovery.DirectoryMonitor: END");
				return;
			}
			
			// first scan: new items will be found relatively to this first scan
			System.out.println("ResourceDiscovery.DirectoryMonitor first scan");
			scanDirectoriesForNewItems(false);
			
			while(ResourceDiscovery.isActive() && monitoredDirectories.size() > 0){
				
				System.out.println("ResourceDiscovery.DirectoryMonitor scanning for diffs");
				scanDirectoriesForNewItems(true);
				
				try {
					Thread.sleep(MONITORING_PERIOD);
				} catch (InterruptedException e) {}
				
			}
			
			try {
				socket.close();
			} catch (Exception e) {}
			
			System.out.println("ResourceDiscovery.DirectoryMonitor: END");
			
		}
		
		private void scanDirectoriesForNewItems(boolean performDiff) {
			for (MonitoredDirectory directory : monitoredDirectories) {
				try {
					updateMonitoredDirectoryItems(directory, performDiff);
//					System.out.println("ResourceDiscovery.DirectoryMonitor newItems for directory " + directory.getUrl() + ": " + directory.getNewItems());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} 
		}

		private void updateMonitoredDirectoryItems(MonitoredDirectory directory, boolean performDiff) throws Exception{
			ServiceResponse httpProxyServer = availableHttpProxyServers.get(directory.getHttpProxyServerNodeId());
			if(httpProxyServer == null){
				throw new Exception("ResourceDiscovery.DirectoryMonitor: httpProxyServer == null");
			}
			Hashtable<String, String> oldItems = directory.getOldItems();
			Hashtable<String, String> newItems = directory.getNewItems();
			String url = directory.getUrl().replaceFirst("http://", "");
			String[] webServerEndPointTokens = url.replaceFirst("/.*", "").split(":"); // extract ipWebServer:port (remove everything after the first /, included)
			String webServerAddr = webServerEndPointTokens[0];
			int webServerPort = Integer.parseInt(webServerEndPointTokens[1]);
			String path = url.replaceFirst("[^/]*/", "/"); // remove everything before the first /

			// save already known items for later comparison
			oldItems.clear();
			oldItems.putAll(newItems);

			// fetch and update new items
			byte[] httpRequest = createHttpRequest(webServerAddr, webServerPort, path);
			InternetRequest request = new InternetRequest(webServerAddr, 
					webServerPort, 
					socket.getLocalPort(), 
					E2EComm.TCP, 
					httpRequest);

			// send the request through the http proxy server
			E2EComm.sendUnicast(httpProxyServer.getServerDest(), 
					httpProxyServer.getServerPort(), 
					httpProxyServer.getProtocol(), 
					E2EComm.serialize(request));

			// receive response
			GenericPacket response = E2EComm.receive(socket, 10*1000);
			if(response instanceof UnicastPacket){
				UnicastPacket up = (UnicastPacket) response;
				// parse the received html
				TagNode root = parseHtml(up.getBytePayload());
				Object[] anchors = null;
				if(directory.getType() == SharedDirectory.PHOTO_TYPE){
					Object[] anchors1 = root.evaluateXPath("//div[@class='thumbs']/a");
					Object[] anchors2 = root.evaluateXPath("//div[@class='linkitem']/a");
					anchors = new Object[anchors1.length + anchors2.length];
					System.arraycopy(anchors1, 0, anchors, 0, anchors1.length);
					System.arraycopy(anchors2, 0, anchors, anchors1.length, anchors2.length);
				}else if(directory.getType() == SharedDirectory.MUSIC_TYPE ||
						 directory.getType() == SharedDirectory.VIDEO_TYPE){
					anchors = root.evaluateXPath("//div[@class='linkitem']/a[last()]");
				}
				// and update the newItems
				for (int i = 0; i < anchors.length; i++) { // foreach <a> node (an item inside the directory)
					if(anchors[i] instanceof TagNode){
						TagNode anchor = (TagNode) anchors[i];
						String href = anchor.getAttributeByName("href");
						
						// XXX twonky contrassegna con SM immagini di bassa qualita;
						// se le seguenti righe sono attive, tali immagini vengono ingorate
						//if(directory.getType() == SharedDirectory.PHOTO_TYPE && href.matches(".+DLNA-PNJPEG_(SM|TN).+")) // thumbnail or small image
						//	continue; // skip
						
						String name = anchor.getText().toString();
						if(name == null || name.equals("")){
							String[] nameTokens = href.split("/");
							name = nameTokens[nameTokens.length - 1]; // extract name from href (everything after last /)
						}
						newItems.put(name, href);
					}
				} // for
				//System.out.println("ResourceDiscovery.DirectoryMonitor: newItems " + newItems);
				if(performDiff){
//					boolean newItemFound = false;
					for (String itemName : newItems.keySet()) {
//						System.out.println("ResourceDiscovery.DirectoryMonitor: checking item for directory " + directory.getUrl() + " (" + itemName + ")");
						if(!oldItems.containsKey(itemName)){
							String link = newItems.get(itemName);
							String rampURL = createRAMPUrl(httpProxyServer.getServerNodeId(), link, true, null);
							String postMessage = "Check out my new ";
							String imgExtsRegEx = "^.+\\.(gif|jpeg|jpg|png|tif|tiff|pbm|pdf)\\??.*$";
							String audioExtsRegEx = "^.+\\.(aif|iff|m3u|m4a|mid|mp3|mpa|ra|wav|wma)\\??.*$";
							String videoExtsRegEx = "^.+\\.(3g2|3gp|asf|asx|avi|flv|mov|mp4|mpg|rm|swf|vob|wmv)\\??.*$";
							boolean isPicture = false;
//							TeamlifePostType teamlifePostType;
							link = link.toLowerCase();
							if(link.matches(imgExtsRegEx)){
								postMessage += "picture!";
								isPicture = true;
//								teamlifePostType = TeamlifePostType.image;
							}else if(link.matches(audioExtsRegEx)){
								postMessage += "audio!";
//								teamlifePostType = TeamlifePostType.audio;
							}else if(link.matches(videoExtsRegEx)){
								postMessage += "video!";
//								teamlifePostType = TeamlifePostType.video;
							}else{
								postMessage += "album!";
//								teamlifePostType = TeamlifePostType.collection;
							}
							
//							SocialObserver.getInstance().getSocialObserverFacebook().postMessageOnUsersWall(postMessage, rampURL, isPicture);
//							SocialObserver.getInstance().getSocialObserverTeamlife().postMessageOnUserWall(postMessage, rampURL, teamlifePostType);
//							System.out.println("ResourceDiscovery.DirectoryMonitor: new item for directory " + directory.getUrl() + " (" + itemName + ")");
//							newItemFound = true;
						}
					}
//					if(!newItemFound)
//						System.out.println("ResourceDiscovery.DirectoryMonitor: any new items for directory " + directory.getUrl());
				}
			}
		}
		
	}
	
	private class MonitoredDirectory {
		
		private int httpProxyServerNodeId;
		private String url;
		private int type;
		private Hashtable<String, String> oldItems; // name, url
		private Hashtable<String, String> newItems; // name, url
		
		public MonitoredDirectory(int httpProxyServerNodeId, String url, int type) {
			this.httpProxyServerNodeId = httpProxyServerNodeId;
			this.url = url;
			this.type = type;
			this.oldItems = new Hashtable<String, String>();
			this.newItems = new Hashtable<String, String>();
		}

		public int getHttpProxyServerNodeId() {
			return httpProxyServerNodeId;
		}
		
		public String getUrl() {
			return url;
		}
		
		public int getType() {
			return type;
		}

		public Hashtable<String, String> getOldItems() {
			return oldItems;
		}

		public Hashtable<String, String> getNewItems() {
			return newItems;
		}

		@Override
		public int hashCode() {
			return 51 + this.httpProxyServerNodeId + url.hashCode() + type;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
    			return true;
    		if (obj == null)
    			return false;
    		if (getClass() != obj.getClass())
    			return false;
    		MonitoredDirectory other = (MonitoredDirectory) obj;
    		if(this.httpProxyServerNodeId == other.getHttpProxyServerNodeId() && this.url.equals(other.getUrl()) && this.type == other.type)
    			return true;
    		return false;
		}
		
		@Override
		public String toString() {
			return "MonitoredDirectory [url=" + url + ", newItems=" + newItems.toString() +"]";
		}
		
	}
	
	
	
	
	
	
	
//	private class UPnPContainerMonitor extends Thread {
//		
//		 // XXX period aggressivo, poi lo rimettiamo a valori piu' consoni
//		private static final int MONITORING_PERIOD = 15 * 1000; //1 * 60 * 1000;
//		private ContentDirectoryControlPoint cdcp = (ContentDirectoryControlPoint)BasicControlPoint.getInstance(RampEntryPoint.getRampProperty("contentDirectoryCP"));
//		
//		@Override
//		public void run() {
//			
//			System.out.println("ResourceDiscovery.UPnPContainerMonitor: START");
//			
//			// first scan: new items will be found relatively to this first scan
//			System.out.println("ResourceDiscovery.UPnPContainerMonitor first scan");
//			scanUPnPContainersForNewItems(false);
//			
//			while(ResourceDiscovery.isActive() /*&& monitoredUPnPContainers.size() > 0*/){
//				
//				System.out.println("ResourceDiscovery.UPnPContainerMonitor scanning for diffs");
//				scanUPnPContainersForNewItems(true);
//				
//				try {
//					Thread.sleep(MONITORING_PERIOD);
//				} catch (InterruptedException e) {}
//				
//			}
//			
//			System.out.println("ResourceDiscovery.UPnPContainerMonitor: END");
//			
//		}
//		
//		private void scanUPnPContainersForNewItems(boolean performDiff) {
//			synchronized (monitoredUPnPContainers) {
//				for (MonitoredUPnPContainer container : monitoredUPnPContainers) {
//					try {
//						updateMonitoredUPnPContainerItems(container, performDiff);
//						System.out.println("ResourceDiscovery.UPnPContainerMonitor newItems for container " + container.getObjectId() + ": " + container.getNewItems());
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
//				} 
//			}
//		}
//		
//		/*
//		 * uShare reindexes all its contents when a new resources is added. 
//		 * In order to keep the right container monitored we have to retrieve its new id by browsing 
//		 * the full path from the root container to the its direct parent container. 
//		 */
//		private String getActualUPnPContainerId(MonitoredUPnPContainer container) {
//			Device device = cdcp.getDevice(container.getDeviceUuid());
//			
//			Stack<UPnPObject> path = (Stack<UPnPObject>) container.getParentsPath().clone(); 
//			UPnPObject parent = path.pop();	// root
//			List<UPnPObject> contents = cdcp.browse((RemoteDevice) device, parent.getObjectId(), ContentDirectoryControlPoint.DEFAULT_PAGESIZE, ContentDirectoryControlPoint.DEFAULT_PAGE);
//			String newId = null;
//			while(!path.isEmpty()) {
//				parent = path.pop();
//				
//				newId = null;
//				for(UPnPObject o : contents) {
//					if(o.getFriendlyName().equals(parent.getFriendlyName())) {
//						newId = o.getObjectId();
//						break;
//					}
//				}
//				
//				if(newId != null)
//					contents = cdcp.browse((RemoteDevice) device, newId, ContentDirectoryControlPoint.DEFAULT_PAGESIZE, ContentDirectoryControlPoint.DEFAULT_PAGE);
//				else
//					break;
//			}
//			return newId;
//		}
//
//		private void updateMonitoredUPnPContainerItems(MonitoredUPnPContainer container, boolean performDiff) throws Exception{
//			
//			ServiceResponse httpProxyServer = availableHttpProxyServers.get(container.getHttpProxyServerNodeId());
//			
//			RemoteDevice device = (RemoteDevice) cdcp.getDevice(container.getDeviceUuid());
//			
//			System.out.println("ResourceDiscovery.UPnPContainerMonitor: Monitoring object " + container.getObjectId());
//			String containerId = getActualUPnPContainerId(container);	// retrieve the new (if changed) id of the container to monitor
//			if(containerId == null) {	// if the id is null, the container has to be removed from monitored containers
//				monitoredUPnPContainers.remove(container);
//				System.out.println("monitoredUPnPContainer removed: it no longer exists (last objectId seen: " + container.getObjectId() + ")");
//				return;
//			}
//			monitoredUPnPContainers.remove(container);
//			container.setObjectId(containerId);
//			monitoredUPnPContainers.add(container);
//			List<UPnPObject> items = cdcp.browse(device, container.getObjectId(), 999, 1);
//			
////			Hashtable<String, String> oldItems = container.getOldItems();
////			Hashtable<String, String> newItems = container.getNewItems();
//			Hashtable<String, UPnPObject> oldItems = container.getOldItems();
//			Hashtable<String, UPnPObject> newItems = container.getNewItems();
//			
//			oldItems.clear();
//			oldItems.putAll(newItems);
//			
//			newItems.clear();
//
////			for(UPnPObject o : items) {
////				String resourceURI = "";
////				if(o.resourceURI != null)
////					resourceURI = o.resourceURI;
////				newItems.put(o.objectId, resourceURI);
////			}
//			for(UPnPObject o : items) {
////				int hashCode = (container.getParentsPathAsString() + o.getFriendlyName()/* + o.getTitle() + o.getType().name() + o.getMediaType().name()*/).hashCode();
////				newItems.put(String.valueOf(hashCode), o);
//				newItems.put(container.getParentsPathAsString() + o.getFriendlyName() + o.getType(), o);
//			}
//			
////			String postMessage2 = "Check out my new album!";
////			String rampURL2 = createRAMPUrl(httpProxyServer.getServerNodeId(), "http://137.204.56.113:6004/ramp://-1036470026:6006:1/http://137.204.56.113:9442/upnp/browse.jsp?did=ce653b44-f760-e719-a79c-44ac22ff7fb2&oid=e194d1ad327b7371899f&bf=children", true, null);
////			System.out.println("ResourceDiscovery.UPnPContainerMonitor: " + postMessage2);
////			SocialObserver.getInstance().getSocialObserverFacebook().postMessageOnUserWall(postMessage2, rampURL2, false);
//			
//			if(performDiff && container.isAlreadyScanned()){
//				for (String itemId : newItems.keySet()) {
//					if(!oldItems.containsKey(itemId)){
////						String link = "http://" + httpProxyServer.getServerDest()[httpProxyServer.getServerDest().length-1] + ":" + (RampWebServer.SSL_PORT-1) + "/upnp/browse.jsp?deviceId=" + container.getDeviceUuid() + "&objectId=" + itemId;
//						String link = "http://" + httpProxyServer.getServerDest()[httpProxyServer.getServerDest().length-1] + ":" + (RampWebServer.SSL_PORT-1) + "/upnp/browse.jsp?did=" + container.getDeviceUuid() + "&oid=" + newItems.get(itemId).getObjectId();
////						String itemResourceURI = newItems.get(itemId);
//						UPnPObject obj = newItems.get(itemId);
//						String itemResourceURI = "";
//						if(obj.getType() == UPnPObjectType.FILE)
//							itemResourceURI = newItems.get(itemId).getResourceURI();
//						String postMessage = "Check out my new ";
//						String imgExtsRegEx = "^.+\\.(gif|jpeg|jpg|png|tif|tiff|pbm|pdf)\\??.*$";
//						String audioExtsRegEx = "^.+\\.(aif|iff|m3u|m4a|mid|mp3|mpa|ra|wav|wma)\\??.*$";
//						String videoExtsRegEx = "^.+\\.(3g2|3gp|asf|asx|avi|flv|mov|mp4|mpg|rm|swf|vob|wmv)\\??.*$";
////						boolean isPicture = false;
////						TeamlifePostType teamlifePostType;
//						if(itemResourceURI.matches(imgExtsRegEx)){
//							link += "&bf=meta";
//							postMessage += "picture!";
////							isPicture = true;
////							teamlifePostType = TeamlifePostType.image;
//						}else if(itemResourceURI.matches(audioExtsRegEx)){
//							link += "&bf=meta";
//							postMessage += "audio!";
////							teamlifePostType = TeamlifePostType.audio;
//						}else if(itemResourceURI.matches(videoExtsRegEx)){
//							link += "&bf=meta";
//							postMessage += "video!";
////							teamlifePostType = TeamlifePostType.video;
//						}else{
//							link += "&bf=children";
//							postMessage += "album!";
////							teamlifePostType = TeamlifePostType.collection;
//						}
////						link = link.toLowerCase();
//						
////						String rampURL = createRAMPUrl(httpProxyServer.getServerNodeId(), link, true, null);
////						String rampURL = createRAMPUrl(httpProxyServer.getServerNodeId(), "http://piripipi.it", true, null);
//						System.out.println("ResourceDiscovery.UPnPContainerMonitor: " + postMessage);
//						
////						boolean succeed = false;
////						for(int maxAttempts=2 ; maxAttempts>0 && !succeed ; maxAttempts--)	// retry if needed
////							succeed = SocialObserver.getInstance().getSocialObserverFacebook().postMessageOnUsersWall(postMessage, rampURL, isPicture);
////						SocialObserver.getInstance().getSocialObserverTeamlife().postMessageOnUserWall(postMessage, rampURL, teamlifePostType);
//					}
//				}
//			}
//			
//			if(!container.isAlreadyScanned())
//				container.setAlreadyScanned(true);
//		}
//	}

//	private class MonitoredUPnPContainer {
//		private String deviceUuid;
//		private String objectId;
//		private int httpProxyServerNodeId;
//		private boolean alreadyScanned;		// has it been scanned at least once?
//		private Stack<UPnPObject> parentsPath;
//		private String parentsPathAsString;
////		private Hashtable<String, String> oldItems;	// objectId, resourceURI
////		private Hashtable<String, String> newItems;	// objectId, resourceURI
//		private Hashtable<String, UPnPObject> oldItems;	// objectId, resourceURI
//		private Hashtable<String, UPnPObject> newItems;	// objectId, resourceURI
//		
//		public MonitoredUPnPContainer(String deviceUuid, String objectId, int httpProxyServerNodeId) {
//			super();
//			this.deviceUuid = deviceUuid;
//			this.objectId = objectId;
//			this.httpProxyServerNodeId = httpProxyServerNodeId;
//			this.alreadyScanned = false;
//			this.parentsPathAsString = null;
////			this.oldItems = new Hashtable<String, String>();
////			this.newItems = new Hashtable<String, String>();
//			this.oldItems = new Hashtable<String, UPnPObject>();
//			this.newItems = new Hashtable<String, UPnPObject>();
//		}
//
//		public String getDeviceUuid() {
//			return deviceUuid;
//		}
//		
//		public String getObjectId() {
//			return objectId;
//		}
//		
//		public void setObjectId(String objectId) {
//			this.objectId = objectId;
//		}
//
//		public int getHttpProxyServerNodeId() {
//			return httpProxyServerNodeId;
//		}
//
//		public boolean isAlreadyScanned() {
//			return alreadyScanned;
//		}
//
//		public void setAlreadyScanned(boolean alreadyScanned) {
//			this.alreadyScanned = alreadyScanned;
//		}
//
//		public Stack<UPnPObject> getParentsPath() {
//			return parentsPath;
//		}
//
//		public void setParentsPath(Stack<UPnPObject> path) {
//			this.parentsPath = path;
//			Stack<UPnPObject> pathToStringify = (Stack<UPnPObject>) getParentsPath().clone();
//			parentsPathAsString = "";
//			while(pathToStringify != null && !pathToStringify.isEmpty()) {
//				parentsPathAsString += pathToStringify.pop().getFriendlyName() + "/";
//			}
//		}
//		
//		public String getParentsPathAsString() {
//			return parentsPathAsString;
//		}
//
////		public Hashtable<String, String> getOldItems() {
////			return oldItems;
////		}
//		public Hashtable<String, UPnPObject> getOldItems() {
//			return oldItems;
//		}
//		
////		public Hashtable<String, String> getNewItems() {
////			return newItems;
////		}
//		public Hashtable<String, UPnPObject> getNewItems() {
//			return newItems;
//		}
//
//		@Override
//		public int hashCode() {
//			final int prime = 31;
//			int result = 1;
//			result = prime * result + getOuterType().hashCode();
//			result = prime * result + ((deviceUuid == null) ? 0 : deviceUuid.hashCode());
//			result = prime * result + httpProxyServerNodeId;
////			result = prime * result + ((objectId == null) ? 0 : objectId.hashCode());
////			result = prime * result + ((parentsPath == null) ? 0 : parentsPath.hashCode());
//			return result;
//		}
//
//		@Override
//		public boolean equals(Object obj) {
//			if (this == obj)
//				return true;
//			if (obj == null)
//				return false;
//			if (getClass() != obj.getClass())
//				return false;
//			MonitoredUPnPContainer other = (MonitoredUPnPContainer) obj;
//			if (!getOuterType().equals(other.getOuterType()))
//				return false;
//			if (deviceUuid == null) {
//				if (other.deviceUuid != null)
//					return false;
//			} else if (!deviceUuid.equals(other.deviceUuid))
//				return false;
//			if (httpProxyServerNodeId != other.httpProxyServerNodeId)
//				return false;
//			if (objectId == null) {
//				if (other.objectId != null)
//					return false;
//			} /*else if (!objectId.equals(other.objectId))
//				return false;*/
//			if (parentsPath == null) {
//				if (other.parentsPath != null)
//					return false;
//			}
//			else {
//				Stack<UPnPObject> path1 = (Stack<UPnPObject>) parentsPath.clone();
//				Stack<UPnPObject> path2 = (Stack<UPnPObject>) other.getParentsPath().clone();
//				while(!path1.isEmpty()) {
//					if(path2.isEmpty())
//						return false;
//					if(!path1.pop().getFriendlyName().equals(path2.pop().getFriendlyName()))	// check only friendly name (objectId could be changed in uShare)
//						return false;
//				}
//				if(!path2.isEmpty())	// now both have to be empty
//					return false;
//			}
////			else if (!parentsPath.equals(other.parentsPath))
////				return false;
//			return true;
//		}
//
//		private ResourceDiscovery getOuterType() {
//			return ResourceDiscovery.this;
//		}
//	}
}
