
package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.RampPacketsProtos;

import java.io.IOException;
import java.io.InputStream;

/**
 * 
 * @author Carlo Giannelli
 */
public class HeartbeatResponse extends it.unibo.deis.lia.ramp.core.e2e.GenericPacket {//implements java.io.Serializable {

	//private static final long serialVersionUID = 3093844000019616260L;
	public static transient final byte PACKET_ID = (byte) 5;

	private int nodeId;
	public HeartbeatResponse(){
		//
	}
	public int getNodeId() {
		return nodeId;
	}
	
	// protobuf stuff
	public void writeToProtos(java.io.OutputStream os) throws java.io.IOException {
    	createProtosHeartbeatResponse().writeDelimitedTo(os);
    }
	public byte[] toProtosByteArray(){
		return createProtosHeartbeatResponse().toByteArray();
	}
	protected RampPacketsProtos.HeartbeatResponse createProtosHeartbeatResponse(){
		RampPacketsProtos.HeartbeatResponse.Builder hRespProtobufBuilder = RampPacketsProtos.HeartbeatResponse.newBuilder();
		
		hRespProtobufBuilder.setNodeId(Dispatcher.getLocalRampId());
    	
		RampPacketsProtos.HeartbeatResponse hRespProtobuf = hRespProtobufBuilder.build();
		return hRespProtobuf;
	}
	
	public static HeartbeatResponse parseFromProtos(InputStream is) throws IOException{
		RampPacketsProtos.HeartbeatResponse hRespProtobuf = RampPacketsProtos.HeartbeatResponse.parseDelimitedFrom(is);
		return createHeartbeatResponse(hRespProtobuf);
	}
	public static HeartbeatResponse parseFromProtos(byte[] bytes, int offset, int length) throws IOException{
		RampPacketsProtos.HeartbeatResponse hRespProtobuf = RampPacketsProtos.HeartbeatResponse.newBuilder().mergeFrom(bytes,offset,length).build();
		return createHeartbeatResponse(hRespProtobuf);
	}
	protected static HeartbeatResponse createHeartbeatResponse(RampPacketsProtos.HeartbeatResponse hRespProtobuf){
		HeartbeatResponse hResp = new HeartbeatResponse();
		
		hResp.nodeId = (int)hRespProtobuf.getNodeId();
		
		return hResp;
	}
	
	@Override
	public byte getPacketId() {
		return PACKET_ID;
	}
	
}
