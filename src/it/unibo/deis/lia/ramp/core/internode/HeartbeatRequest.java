
package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.RampPacketsProtos;

import java.io.IOException;
import java.io.InputStream;

/**
 * 
 * @author Carlo Giannelli
 */
public class HeartbeatRequest extends it.unibo.deis.lia.ramp.core.e2e.GenericPacket {//implements java.io.Serializable {

	//private static final long serialVersionUID = 6877831114267103030L;
	
	public static transient final byte PACKET_ID = (byte) 4;
	
	private int nodeId;
	public HeartbeatRequest(){
		//
	}
	public int getNodeId() {
		return nodeId;
	}
	
	
	// protobuf stuff
    public void writeToProtos(java.io.OutputStream os) throws java.io.IOException {
    	createProtosHeartbeatRequest().writeDelimitedTo(os);
    }
	public byte[] toProtosByteArray(){
		return createProtosHeartbeatRequest().toByteArray();
	}
	protected RampPacketsProtos.HeartbeatRequest createProtosHeartbeatRequest(){
		RampPacketsProtos.HeartbeatRequest.Builder hReqProtobufBuilder = RampPacketsProtos.HeartbeatRequest.newBuilder();
		
		hReqProtobufBuilder.setNodeId(Dispatcher.getLocalRampId());
    	
		RampPacketsProtos.HeartbeatRequest hReqProtobuf = hReqProtobufBuilder.build();
		return hReqProtobuf;
	}
	
	public static HeartbeatRequest parseFromProtos(InputStream is) throws IOException{
		RampPacketsProtos.HeartbeatRequest hReqProtobuf = RampPacketsProtos.HeartbeatRequest.parseDelimitedFrom(is);
		return createHeartbeatRequest(hReqProtobuf);
	}
	public static HeartbeatRequest parseFromProtos(byte[] bytes, int offset, int length) throws IOException{
		RampPacketsProtos.HeartbeatRequest hReqProtobuf = RampPacketsProtos.HeartbeatRequest.newBuilder().mergeFrom(bytes,offset,length).build();
		return createHeartbeatRequest(hReqProtobuf);
	}
	protected static HeartbeatRequest createHeartbeatRequest(RampPacketsProtos.HeartbeatRequest hReqProtobuf){
		HeartbeatRequest hReq = new HeartbeatRequest();
		hReq.nodeId = (int)hReqProtobuf.getNodeId();
		return hReq;
	}
	
	@Override
	public byte getPacketId() {
		return PACKET_ID;
	}
	
}
