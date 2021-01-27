package it.unibo.deis.lia.ramp.service.management;

import it.unibo.deis.lia.ramp.core.internode.Heartbeater;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

/**
 * 
 * @author Lorenzo Donini
 *
 */
public class ServiceDiscoveryAgent {
	private static final long DEFAULT_DISCOVERY_INTERVAL = 60000;
	private static final int DEFAULT_THREAD_TTL = 3;
	private static final int MAX_HOP_COUNT=5;	
	private static ServiceDiscoveryAgent mAgent;
	private IAgentDiscoveryThreadListener mListener;
	private ServiceDiscoveryThread mThread;
	
	private ServiceDiscoveryAgent() 
	{}
	
	public static ServiceDiscoveryAgent getInstance()
	{
		if(mAgent==null)
		{
			mAgent=new ServiceDiscoveryAgent();
		}
		return mAgent;
	}
	
	public void setOnAgentDiscoveryListener(IAgentDiscoveryThreadListener listener)
	{		
		mListener = listener;
	}
	
	public Collection<ServiceResponse> serviceNamesDiscovery(boolean bAsynchronous) throws Exception
	{
		if(bAsynchronous)
		{
			if(mThread == null || !mThread.isAlive())
			{
				mThread = new ServiceDiscoveryThread();
				mThread.start();
			}
			else
			{
				mThread.refreshThread();
			}
			return null;
		}
		Collection<ServiceResponse> responses = new ArrayList<ServiceResponse>();
		discoveryByFlooding(responses); //Default discovery method
		
		return responses;
	}
	
	public void stopAsynchronousDiscovery()
	{
		if(mThread != null)
		{
			mThread.interrupt();
			mThread = null;
		}
	}		
	
	//################## DISCOVERY METHODS ##################//
	/**
	 * Performs a Service discovery operation, targeting every known Ip address. The Ip
	 * addresses are retrieved from the Heartbeater component, and belong to the Neighbor nodes.
	 * By default this method doesn't not perform remote requests on Ip addresses that are
	 * connected to the ERN Manager, meaning this method doesn't exploit Internet access.
	 * Since Neighbors can be found on multihop paths though, the Ip addresses on which
	 * the ServiceDiscovery.getServices(...) method will be called can be multihop as well.
	 * The method performs a blocking call using Unicast Packets instead of Broadcast, and
	 * stores the found ServiceResponse objects inside the Collection passed as input.
	 * 
	 * @param responses  A Collection of ServiceResponse in which the newly found services
	 * will be added. The passed Collection must be initialized and can already contain items,
	 * which will not be overridden in case a copy of an existing object is found during
	 * the discovery process.
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	private synchronized static void discoveryByAddresses(Collection<ServiceResponse> responses) throws Exception
	{
		Vector<InetAddress> addresses = Heartbeater.getInstance(true).getNeighbors();
		addresses.add(InetAddress.getLocalHost());
		
		System.out.println("ServiceDiscoveryAgent.discoveryByAddresses started!");
		for(InetAddress a: addresses)
		{
			//System.out.println("DiscoveryByAddress: "+a.getHostAddress());
			responses.addAll(ServiceDiscovery.getServices(new String [] {a.getHostAddress()}));
		}
	}
	
	/**
	 * Performs a Service discovery operation, first obtaining all services currently active
	 * on the local node, then broadcasting a service request. The broadcast procedure
	 * uses flooding, meaning it will try reaching every possible Ramp node until the
	 * TTL expires. The broadcast reaches remote nodes connected to the ERN Manager as well,
	 * both in case the caller node is the ERN Manager or is simply a RIN.
	 * The method performs a blocking call on ServiceDiscovery.findAllServices(...), using
	 * Broadcast Packets and storing all the found ServiceResponse objects inside the Collection
	 * passed as input.
	 * 
	 * @param responses  A Collection of ServiceResponse in which the newly found services
	 * will be added. The passed Collection must be initialized and can already contain items,
	 * which will not be overridden in case a copy of an existing object is found during
	 * the discovery process.
	 * @throws Exception
	 */
	private synchronized static void discoveryByFlooding(Collection<ServiceResponse> responses) throws Exception
	{		
		String localhost [] = new String [] {GeneralUtils.getLocalHost(true)};		
		
		System.out.println("ServiceDiscoveryAgent.discoveryByFlooding started!");
		responses.addAll(ServiceDiscovery.getServices(localhost));
		responses.addAll(ServiceDiscovery.getAllServices(MAX_HOP_COUNT));
	}
	
	/**
	 * 
	 * @author Lorenzo Donini
	 *
	 */
	public interface IAgentDiscoveryThreadListener {
		public void onServiceNamesDiscovered(Collection<ServiceResponse> responses);
		public void onThreadInterrupted();
	}
	
	/**
	 * 
	 * @author Lorenzo Donini
	 *
	 */
	private class ServiceDiscoveryThread extends Thread{
		private int refreshNum;
		Collection<ServiceResponse> mResponses;
		
		public ServiceDiscoveryThread()
		{
			mResponses = new ArrayList<ServiceResponse>();
			refreshNum = 0;
		}
		
		@Override
		public void run()
		{
			do
			{
				try{
					mResponses.clear();
					discoveryByFlooding(mResponses);
					if(mListener != null)
					{
						mListener.onServiceNamesDiscovered(mResponses);
					}
					synchronized(this) {
						refreshNum++;
					}
					Thread.sleep(DEFAULT_DISCOVERY_INTERVAL); //Waiting
				}
				catch(InterruptedException e)
				{
					if(mListener != null)
					{
						mListener.onThreadInterrupted();
					}
					System.out.println("ServiceDiscoveryAgent.ServiceDiscoveryThread: Interrupted");
				}				
				catch(Exception e)
				{
					//TODO: put error here
				}
			} while(getRefreshNum() < DEFAULT_THREAD_TTL);
			if(mListener != null)
			{
				mListener.onThreadInterrupted();
			}
		}
		
		private synchronized int getRefreshNum()
		{
			return refreshNum;
		}
		
		private synchronized void refreshThread()
		{
			refreshNum=0;
		}
	}
}
