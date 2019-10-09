package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import java.util.Map;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		

		/********************************************************************/
		/* TODO: Handle packets                                             */
		//check if IPv4 Packet, if not drop
		if(etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
			System.out.println("Drop! type is not IPv4");
			return;
		}		

		//verify checksum
		IPv4 ipPkt = (IPv4)etherPacket.getPayload();
		short receivedChecksum = ipPkt.getChecksum();
		ipPkt.setChecksum((short) 0);
		byte[] data = ipPkt.serialize();
		ipPkt = (IPv4)ipPkt.deserialize(data, 0, data.length);
		if(receivedChecksum!=ipPkt.getChecksum()){
			System.out.println("Drop! checksum not match");
			return;
		}

		//decrement TTL drop if zero
		byte ttl = ipPkt.getTtl();
		if((int)ttl < 1){
			System.out.println("Drop! ttl<1");
			return;
		}
		ipPkt.setTtl((byte)(ttl -1));

		//check if ip equals one of the router's interface, if true then drop
		for(Map.Entry<String,Iface> interfaceEntry: this.interfaces.entrySet()){
			if(ipPkt.getDestinationAddress() == interfaceEntry.getValue().getIpAddress()){
				System.out.println("Drop! dst ip == one of the router's interface");
				return;
			}
		}

		//get forward routetable entry, if no entry matches, drop
		RouteEntry rtEntry = routeTable.lookup(ipPkt.getDestinationAddress());
		if(rtEntry == null){
			System.out.println("Drop! no matching routing table entry");
			return;
		}

		//compute the new checksum
		data = ipPkt.serialize();
		ipPkt = (IPv4)ipPkt.deserialize(data, 0, data.length);

		//look up dst mac
		ArpEntry arpEntry;
		if(rtEntry.getGatewayAddress()==0){
			arpEntry = arpCache.lookup(ipPkt.getDestinationAddress());
		}else{
			arpEntry = arpCache.lookup(rtEntry.getGatewayAddress());
		}
		if(arpEntry!=null){
			Ethernet forwardPkt = (Ethernet)etherPacket.setPayload(ipPkt);
			String srcMac = rtEntry.getInterface().getMacAddress().toString();
			String dstMac = arpEntry.getMac().toString();
			forwardPkt.setSourceMACAddress(srcMac);
			forwardPkt.setDestinationMACAddress(dstMac);
			sendPacket(forwardPkt, rtEntry.getInterface());
		}
		





		/********************************************************************/
	}
}
