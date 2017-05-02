package it.unibo.deis.lia.ramp.service.management;

//import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.internode.Heartbeater;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscoveryAgent.IAgentDiscoveryThreadListener;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscoveryAgent;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
//import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
//import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Support class, needed in order to manage and maintain locally all the information
 * regarding reachable RAMP nodes. This manager is responsible for performing
 * discovery operations ({@link getAllServices} flooding) that grant global
 * visibility of a RAMP network, including all nodes inside a federation.
 * 
 * @author Lorenzo Donini
 *
 */
public class RampNetworkManager {	//
	private boolean bDiscovering;
	private Map<String, Collection<ServiceResponse>> mNetworkMap;
	public static final String FILTERS_LOADED_FLAG = "bFiltersLoaded";
	public static final String SERVICE_MANAGER_KEY = "serviceManager";
	public static final long DEFAULT_DISCOVERY_INTERVAL = 60000;
	public static final int DEFAULT_DISCOVERY_TIMEOUT=5000;
	public static final int MAX_HOP_COUNT=5;
	public static final int SERVICE_AMOUNT=5;
	
	public RampNetworkManager()
	{
		//mServices = new Vector<ServiceResponse>();
		mNetworkMap = new HashMap<String, Collection<ServiceResponse>>();
		bDiscovering=false;		
//		checkFilterRules();
	}
	
	//CRAPPY TEMP FUNCTION
//	private void checkFilterRules()
//	{
//		Map<RuleKey, FilterRule> rules = FilterRulesManager.getInstance().getRulesByFilterType("AF");
//		int i=0;
//		if(rules.isEmpty())
//		{
//			System.out.println("############### EMPTY!");
//		}
//		for(RuleKey r: rules.keySet())
//		{
//			System.out.println("Rule "+i+" - Owner: "+r.getOwnerId());
//			i++;
//			FilterRule f = rules.get(r);
//			System.out.println("----- Filter "+f.getFilterType()+": "+f.getRule().getScope());
//		}
//	}
	
	//################## GETTERS & SETTERS ##################//
	private synchronized Collection<ServiceResponse> getAllInternalServices()
	{
		Collection<ServiceResponse> services = new ArrayList<ServiceResponse>();
		if(mNetworkMap != null)
		{
			for(String key: mNetworkMap.keySet())
			{
				services.addAll(mNetworkMap.get(key));
			}
		}
		return services;
	}
	
	public synchronized Collection<String []> getAllAddresses()
	{
		Collection<ServiceResponse> services = getAllInternalServices();
		Collection<String []> addresses = new ArrayList<String []>();
		for(ServiceResponse s: services)
		{
			addresses.add(s.getServerDest());
		}
		return addresses;
	}
	
	/**
	 * Provides a Mapping of all found services, using the relative RINs as keys for the
	 * mappings. Each key is associated to a Collection of ServiceResponse objects, that
	 * represent all Services active on Devices connected to the same Ramp network where
	 * the RIN resides in.
	 * 
	 * @return  Returns a Mapping of <RinIpAddress, Services>.
	 */
	public synchronized Map<String, Collection<ServiceResponse>> getMappedServices()
	{
		return this.mNetworkMap;
	}
	
	/**
	 * Provides a Set containing all the names of the found Services stored in the
	 * {@link RampNetworkManager} object. The function does not perform any discovery operation, 
	 * nor calls any core layer primitive. It just accesses a previously created map
	 * of services and returns their names. The result doesn't contain any duplicates,
	 * since the returned data structure is a Set.
	 * 
	 * @return  Returns a {@link Set} of all the available services, with no duplicates
	 */
	public synchronized Set<String> getAvailableServiceNames()
	{
		Set<String> result = new HashSet<String>();
		Collection<ServiceResponse> services = getAllInternalServices();
		
		for(ServiceResponse s: services)
		{
			result.add(s.getServiceName());
		}
		return result;
	}
	
	/**
	 * Provides a Mapping of Services grouped by the Id of the RAMP Node they are
	 * running on. In particular, a {@link Collection} containing all the {@link ServiceResponse} 
	 * objects retrieved from the RAMP Node that responded to a service discovery request, is
	 * associated to each Integer key, which represents the node Identifier.
	 * 
	 * @return Returns a HashMap containing all the {@link ServiceResponse} objects, using the
	 * RAMP node IDs as the map keys
	 */
	public synchronized Map<Integer, Collection<ServiceResponse>> getServicesByNodeId()
	{
		Map<Integer, Collection<ServiceResponse>> result=null;
		Collection<ServiceResponse> value;
		Collection<ServiceResponse> services = getAllInternalServices();
		
		if(services != null)
		{
			result = new HashMap<Integer, Collection<ServiceResponse>>();
			for(ServiceResponse s: services)
			{
				if(result.containsKey(s.getServerNodeId()))
				{
					result.get(s.getServerNodeId()).add(s);
				}
				else
				{
					value = new ArrayList<ServiceResponse>();					
					value.add(s);
					result.put(s.getServerNodeId(), value);
				}
			}
		}
		return result;
	}
	
	/**
	 * Provides a Mapping of Services grouped by the single Service names. Therefore
	 * all items referenced by a key will be instances of the same Service running
	 * on different Ramp nodes. In case different Ramp nodes have the same IP address,
	 * even though they belong to different networks, all of the nodes will be
	 * included in a Collection<ServiceResponse> item referenced by a Service name.
	 * 
	 * @return Returns a {@link Map} containing all the ServiceResponse objects, using the
	 * Service names as the map keys
	 */
	public synchronized Map<String, Collection<ServiceResponse>> getServicesByServiceName()
	{
		Map<String, Collection<ServiceResponse>> result=null;
		Collection<ServiceResponse> value;
		Collection<ServiceResponse> services = getAllInternalServices();
		
		if(services != null)
		{
			result = new HashMap<String, Collection<ServiceResponse>>();
			for(ServiceResponse s: services)
			{
				if(result.containsKey(s.getServiceName()))
				{
					result.get(s.getServiceName()).add(s);
				}
				else
				{
					value = new ArrayList<ServiceResponse>();
					value.add(s);
					result.put(s.getServiceName(), value);
				}
			}
		}
		return result;
	}
	
	/**
	 * Provides a Mapping of Services grouped by the IP address of the RAMP nodes
	 * the services are running on. Similar to {@link #getServicesByNodeId()}, the map items
	 * are Collections containing all Services running on a single IP address. In case
	 * there were multiple RAMP nodes with the same IP, then the entries would be
	 * merged together.
	 * 
	 * @return Returns a {@link Map} containing all the {@link ServiceResponse} objects, 
	 * using the RAMP node IP addresses as the map keys
	 */
	public synchronized Map<String, Collection<ServiceResponse>> getServicesByIp()
	{
		Map<String, Collection<ServiceResponse>> result=null;
		Collection<ServiceResponse> value;
		Collection<ServiceResponse> services = getAllInternalServices();
		String ip;
		
		if(services != null)
		{
			result = new HashMap<String, Collection<ServiceResponse>>();
			for(ServiceResponse s: services)
			{
				ip=s.getServerDest()[s.getServerDest().length-1];
				if(result.containsKey(ip))
				{
					result.get(ip).add(s);
				}
				else
				{
					value = new ArrayList<ServiceResponse>();
					value.add(s);
					result.put(ip, value);
				}
			}
		}
		return result;
	}
	
	/**
	 * Provides a Collection containing all the found instances of a specific Service,
	 * given the service name. Usually, two instances of the same service cannot
	 * run on the same Ramp node, therefore all items contained in the resulting Collection
	 * will be references of service instances running on different Ramp nodes.
	 * 
	 * @param name  A specific Service name to use as search criteria
	 * @return  Returns a {@link Collection} of {@link ServiceResponse} items, in which are 
	 * contained only ServiceResponses associated to services matching the String given in input
	 */
	public synchronized Collection<ServiceResponse> getServicesByName(String name)
	{
		Collection<ServiceResponse> result = null;
		Collection<ServiceResponse> services = getAllInternalServices();
		
		if(services != null)
		{
			result = new ArrayList<ServiceResponse>();
			for(ServiceResponse s: services)
			{
				if(s.getServiceName().equals(name))
				{
					result.add(s);
				}
			}
		}
		return result;
	}
	
	/**
	 * Provides a Collection containing only services belonging to a specific RIN; in other
	 * words only services active on nodes inside the private RAMP Network where of the e given RIN.
	 * Returned services are retrieved from an internal Map and can belong to different Devices 
	 * and private IP addresses.
	 * 
	 * @param rinAddress  The IPv4 address of a RAMP Internet Node.
	 * @return  Returns a {@link Collection} of {@link ServiceResponse} items, of which each service 
	 * resides in the private network of the RIN address passed as a parameter.
	 */
	public synchronized Collection<ServiceResponse> getServicesByRinAddress(String rinAddress)
	{
		if(mNetworkMap != null)
		{
			return mNetworkMap.get(rinAddress);
		}
		return null;
	}
	
	/**
	 * Provides a Collection containing only services belonging to the local RAMP Network, which
	 * includes all RAMP nodes residing in the private network and its' subnets.
	 * Returned services are retrieved from an internal Map and can belong to different Devices and
	 * private IP addresses, but all are connected to the local RIN, without needing to
	 * access the Internet in order to communicate.
	 * 
	 * @return  Returns a {@link Collection} of descriptors, associated to services running
	 * only locally.
	 */
	public synchronized Collection<ServiceResponse> getLocalNetworkServices()
	{
		String publicIp = GeneralUtils.getMyPublicIpString();
		if(mNetworkMap != null)
		{
			return mNetworkMap.get(publicIp);
		}
		return null;
	}
	
	/**
	 * This method sets an internal variable, mapping each Service descriptor passed as 
	 * a parameter inside the Collection object to the corresponding RIN address.
	 * The method can only be invoked internally, and is automatically invoked after a
	 * Discovery operation. When invoked, it first clears the previously stored variable,
	 * then replaces it with a {@link Map} object that contains all the {@link ServiceResponse} 
	 * items found on all reachable RAMP nodes, both local and remote. These items are although 
	 * mapped to different keys, each of which represents the public address of a RIN.
	 * 
	 * @param services  The {@link Collection} containing {@link ServiceResponse} objects, 
	 * previously found by a discovery method.
	 */
	protected synchronized void setNetworkMap(Collection<ServiceResponse> services)
	{
		mNetworkMap.clear();
		if(services == null)
		{
			return;
		}
		mapLocalServices(services);
//		mapRemoteServices(services,getNetworkRinAddresses());
	}		
	
	/**
	 * Seeks through the connected ERNs in order to retrieve all the RINs this RAMP
	 * node is connected to, single-hop or multi-hop. This method behaves in a basic way,
	 * finding all RINs, regardless of the Social Profile they are associated to.
	 * 
	 * @return  Returns a {@link Set} containing Information regarding all the found RINs.
	 * No duplicates are returned.
	 */
//	public Set<String> getNetworkRinAddresses()
//	{
//		Set<String> rinAddresses = new HashSet<String>();
//		Enumeration<RemoteErnInformation> erns = SecureJoinEntrypoint.getConnectedErns();
//		
//		while(erns.hasMoreElements())
//		{
//			RemoteErnInformation ern = erns.nextElement();
//			for(ConnectedRinInformationSentToOtherRins rin: ern.getOtherRinListAddress())
//			{
//				rinAddresses.add(GenericPacket.i2s(rin.getInternetAddress()));
//			}			
//		}
//		return rinAddresses;
//	}
	
	/**
	 * Given a Collection of ServiceResponse items, iterates through the Collection in order
	 * to retrieve only the Services residing on local Ramp nodes. This method doesn't
	 * contemplate loops in paths, meaning that if multiple RINs are residing inside
	 * the same local Subnet, then all of them will be treated as local Nodes.
	 * Multi-hop nodes are found by using a list of Neighbors, obtained from the
	 * {@link Heartbeater} component. If one of these neighbors is an intermediary, then the
	 * node is in an attached Subnet, therefore it is considered as a local one.
	 * 
	 * @param services  A {@link Collection} of Service descriptors, previously found by a 
	 * discovery method. The items are in no way modified or removed, and the Collection is retained.
	 */
	private void mapLocalServices(Collection<ServiceResponse> services)
	{
		Vector<InetAddress> neighbors = Heartbeater.getInstance(true).getNeighbors();
		String myIp = GeneralUtils.getMyPublicIpString();
		Collection<ServiceResponse> localServices = new ArrayList<ServiceResponse>();
		String localIp = null;
		
		try {
			localIp = InetAddress.getLocalHost().getHostAddress();
		} 
		catch (UnknownHostException e) 
		{		
			//Should never be reached
			System.err.println("RampNetworkManager.mapLocalServices: ERROR! Couldn't resolve localhost");
			return;
		}
		
		for(ServiceResponse s: services)
		{			
			String firstHop = s.getServerDest()[0];
			if(firstHop.equals(localIp))
			{
				localServices.add(s);
				continue;
			}
			for(InetAddress addr: neighbors)
			{
				if(addr.getHostAddress().equals(firstHop))
				{
					localServices.add(s);
					break;
				}
			}
		}
		//Adding Local Services to Map
		mNetworkMap.put(myIp, localServices);
	}
	
	/**
	 * Given a Collection of ServiceResponse items, iterates through the Collection in order
	 * to retrieve only the Services residing on remote Ramp nodes. Multi-hop paths are
	 * handled automatically by finding intermediary RIN nodes. 
	 * Specifically, for each path on which a Service resides, the method searches through 
	 * the Set of RINs passed as a parameter in order to see if one of the intermediary 
	 * nodes matches a certain RIN public address. All Nodes that don't have an intermediary
	 * node, which is contained in the passed RIN white-list, are ignored.
	 * 
	 * @param services  A {@link Collection} of Services, previously found by a discovery method.
	 * The items are in no way modified or removed, and the Collection is retained.
	 * @param rins  A {@link Set} of RINs, containing their public IP address and other metadata.
	 */
//	private void mapRemoteServices(Collection<ServiceResponse> services, 
//			Set<String> rinAddresses)
//	{
//		boolean found=false;					
//		Collection<ServiceResponse> remoteServices = null;
//		Iterator<String> rinIterator = null;
//		String rinPublicIp = null;
//		
//		for(ServiceResponse s: services)
//		{			
//			rinIterator=rinAddresses.iterator();
//			while(rinIterator.hasNext() && !found)
//			{				
//				rinPublicIp = rinIterator.next();
//				for(String hop: s.getServerDest())
//				{
//					if(hop.equals(rinPublicIp))
//					{
//						found=true;
//						break;
//					}
//				}
//			}
//			//In case no match was found, the service does not belong to a known RIN
//			if(!found)
//			{
//				continue;
//			}
//			
//			remoteServices = mNetworkMap.get(rinPublicIp);
//			if(remoteServices == null)
//			{
//				remoteServices = new ArrayList<ServiceResponse>();
//				remoteServices.add(s);
//				mNetworkMap.put(rinPublicIp, remoteServices);
//			}
//			else
//			{
//				remoteServices.add(s);
//			}
//			found=false;
//		}
//	}
	
	//################## DISCOVERY METHODS ##################//
	/**
	 * Starts the discovery operation, delegating the flooding mechanism to the dedicated
	 * {@link ServiceDiscoveryAgent} component. The request can be synchronous for one-time
	 * use only, or asynchronous for periodic calls. After each discovery, the method
	 * provides to map the found services internally.
	 * 
	 * @param bEnableBackgroundThread  Flag used in case the discovery needs to be periodic
	 * and asynchronous.
	 */
	public void startServicesDiscovery(boolean bEnableBackgroundThread)
	{
		ServiceDiscoveryAgent agent = ServiceDiscoveryAgent.getInstance();
		try{			
			if(bEnableBackgroundThread)
			{
				agent.setOnAgentDiscoveryListener(new IAgentDiscoveryThreadListener(){

					@Override
					public void onServiceNamesDiscovered(Collection<ServiceResponse> responses) 
					{
						setNetworkMap(responses);
						//setAvailableServices(responses);	
					}

					@Override
					public void onThreadInterrupted() 
					{
						setDiscovering(false);					
					}					
				});
				agent.serviceNamesDiscovery(bEnableBackgroundThread);			
			}
			else
			{
				Collection<ServiceResponse> responses = agent.serviceNamesDiscovery(false);
				setNetworkMap(responses);
				//this.setAvailableServices(responses);
			}
		}
		catch(Exception e)
		{
			//TODO: NEED TO HANDLE THIS
		}
	}
	
	private synchronized void setDiscovering(boolean discovering)
	{
		bDiscovering=discovering;
	}
	
	/**
	 * Gives information about the current status of the {@link ServiceDiscoveryAgent} component.
	 * In case no previous asynchronous discovery was performed, the component will not be active, 
	 * therefore the Manager is not discovering.
	 * 
	 * @return  Returns true if an asynchronous discovery is being performed, false otherwise.
	 */
	public synchronized boolean isDiscovering()
	{
		return bDiscovering;
	}		
	
//	public Set<SecureJoinUser> getUsers(String user, byte [] pw)
//	{
//		Set<SecureJoinUser> users=null;
//		try {
//			users = SecureJoinEntrypoint.getInstance().getUserList(user, pw);			
//		} 
//		catch (Exception e) 
//		{			
//			e.printStackTrace();
//		}		
//		return users;
//	}
}
