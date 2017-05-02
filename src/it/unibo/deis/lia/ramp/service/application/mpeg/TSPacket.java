/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

//import java.util.*;

/**
 *
 * @author useruser
 */
public class TSPacket implements 
						//java.io.Serializable,
						java.io.Externalizable {
	
	public TSPacket(){} // required by Externalizable
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7862034680586236963L;
	
	private boolean payloadUnitStart;
    private short pid;
    private byte frameType;
    private byte[] tsPacketByte;

    transient private int continuityCounter;
    transient private int payloadStart;
    transient private boolean adaptationFieldExist;
    transient private int adaptationFieldLength;
    
    public TSPacket(byte[] tsPacketByte){
        this.tsPacketByte = tsPacketByte;
    }

    public TSPacket(
            boolean payloadUnitStart,
            short pid,
            byte[] tsPacketByte) {
        this.payloadUnitStart = payloadUnitStart;
        this.pid = pid;
        this.tsPacketByte = tsPacketByte;
        this.frameType = TSPacket.UNDEFINED;
    }
    
    public TSPacket(
            boolean payloadUnitStart,
            short pid,
            int continuityCounter,
            byte[] tsPacketByte,
            int payloadStart,
            boolean adaptationFieldExist,
            int adaptationFieldLength) {
        this.payloadUnitStart = payloadUnitStart;
        this.pid = pid;
        //this.continuityCounter = continuityCounter;
        this.tsPacketByte = tsPacketByte;
        this.payloadStart = payloadStart;
        //this.adaptationFieldExist = adaptationFieldExist;
        //this.adaptationFieldLength = adaptationFieldLength;
        this.frameType = TSPacket.UNDEFINED; // null;
    }
    
    /*public void sliceAt(byte sliceType, short sliceStart){
    	//System.out.println("TSPacket.sliceAt sliceType="+sliceType+" sliceStart="+sliceStart);
    	if(sliceStarts==null){
    		sliceStarts = new short[1];
    		sliceTypes = new byte[1];
    	}
    	else{
    		sliceStarts = Arrays.copyOf(sliceStarts, sliceStarts.length+1);
    		sliceTypes = Arrays.copyOf(sliceTypes, sliceTypes.length+1);
    	}
    	sliceStarts[sliceStarts.length-1] = sliceStart;
    	sliceTypes[sliceTypes.length-1] = sliceType;
    }
    
    public boolean tailorSlice(float iDropProb, float pbDropProb){
    	boolean delete = false;
    	Random r = new Random();
    	System.out.println("TSPacket.tailorSlice iDropProb="+iDropProb+" pbDropProb="+pbDropProb+" #slices"+this.sliceTypes.length);
    	//boolean stop = false;
    	for(int i=0; !delete && i<sliceTypes.length; i++){
        	float random = r.nextFloat();
        	System.out.println("TSPacket.tailorSlice sliceTypes["+i+"]="+sliceTypes[i]+" random="+random);
        	if( sliceTypes[i]==TSPacket.I_IDR ){
    			if( random < iDropProb ){
    				// drop the whole TS packet 
    				delete = true;
    			}
    		}
			else if( sliceTypes[i]==TSPacket.P_SLICE 
					|| sliceTypes[i]==TSPacket.B_SLICE ){
				if( random < pbDropProb ){
			    	System.out.println("TSPacket.tailorSlice removing P/B NALU");
					int diff = 0;
					if( i==sliceStarts.length-1 ){
						// last NALU
		            	System.out.println("last NALU: sliceStarts[i] "+sliceStarts[i]);
						byte[] resultTsPacketByte = new byte[sliceStarts[i]];
						System.arraycopy(tsPacketByte, 0, resultTsPacketByte, 0, sliceStarts[i]);
						tsPacketByte = resultTsPacketByte;
					}
					else{
						// NOT last NALU
						//System.out.println();
		            	//System.out.println("NOT last NALU: tsPacketByte.length "+tsPacketByte.length);
		            	//System.out.println("NOT last NALU: sliceStarts[i+1] "+sliceStarts[i+1]);
		            	System.out.println("NOT last NALU: sliceStarts[i] "+sliceStarts[i]);
						diff = sliceStarts[i+1]-sliceStarts[i];
						byte[] resultTsPacketByte = new byte[tsPacketByte.length - diff];
						//System.out.println("NOT last NALU: resultTsPacketByte.length "+resultTsPacketByte.length);
						System.arraycopy(tsPacketByte, 0, resultTsPacketByte, 0, sliceStarts[i]);
						System.arraycopy(tsPacketByte, sliceStarts[i+1], resultTsPacketByte, sliceStarts[i], tsPacketByte.length-sliceStarts[i+1]);
						tsPacketByte = resultTsPacketByte;
					}
						
					byte[] resultSliceTypes = new byte[sliceTypes.length - 1];
					System.arraycopy(sliceTypes, 0, resultSliceTypes, 0, i);
					if (sliceTypes.length != i) {
					    System.arraycopy(sliceTypes, i + 1, resultSliceTypes, i, sliceTypes.length - i - 1);
					}
					sliceTypes = resultSliceTypes;
					
					short[] resultSliceStarts = new short[sliceStarts.length - 1];
					System.arraycopy(sliceStarts, 0, resultSliceStarts, 0, i);
					for(int index=i; index<resultSliceStarts.length; index++){
						resultSliceStarts[index] = (short)(sliceStarts[index+1] - diff);
					}
					//if (sliceStarts.length != i) {
					//    System.arraycopy(sliceStarts, i + 1, resultSliceStarts, i, sliceStarts.length - i - 1);
					//}
					sliceStarts = resultSliceStarts;
					
					i--;
					if(sliceTypes.length<=1){
						delete = true;
					}
				}
			}
        	System.out.println("TSPacket.tailorSlice #slices"+this.sliceTypes.length);
    	}
    	return delete;
    }*/

    //public void setFrameType(String frameType) {
    public void setFrameType(byte frameType) {
        this.frameType = frameType;
    }
    
    //public String getFrameType() {
    public byte getFrameType() {
        return frameType;
    }
    
    public int getContinuityCounter() {
        return continuityCounter;
    }

    public boolean isPayloadUnitStart() {
        return payloadUnitStart;
    }

    public byte[] getTsPacketByte() {
        return tsPacketByte;
    }

    public short getPid() {
        return pid;
    }

    public int getPayloadStart() {
        return payloadStart;
    }

    public boolean isAdaptationFieldExist() {
        return adaptationFieldExist;
    }

    public int getAdaptationFieldLength() {
        return adaptationFieldLength;
    }
    
    // ---------------------------------------------------

	transient public static final byte UNDEFINED = -1;
	
	transient public static final byte AUDIO = 0;
	
	// [1,9] range ==> frame (mpeg2 & h264) 
	transient public static final byte I_FRAME = 1;			// mpeg2
	transient public static final byte P_FRAME = 2;			// mpeg2
	transient public static final byte B_FRAME = 3;			// mpeg2
	transient public static final byte D_FRAME = 4;			// mpeg2
	transient public static final byte IDR_FRAME = 5;		// h264 Instantaneous Decoding Refresh (IDR)
	transient public static final byte NON_IDR_FRAME = 6;	// h264

	// [5,49] range ==> slice (h264) 
	transient public static final byte GENERIC_SLICE = 10;
	transient public static final byte AUD_SLICE = 11; // Access Unit Delimiter
	transient public static final byte I_SLICE = 12;
	transient public static final byte P_SLICE = 13;
	transient public static final byte B_SLICE = 14;
	transient public static final byte SI_SLICE = 15;
	transient public static final byte SP_SLICE = 16;
	transient public static final byte NON_VCL_SLICE = 17;
	
	// [50,-] NO AUDIO OR VIDEO
	transient public static final byte UNKNOWN_TABLE = 50;
	transient public static final byte PAT = 51;
	transient public static final byte PMT = 52;
	transient public static final byte NIT_THIS_NET = 53;
	transient public static final byte NIT_OTHER_NET = 54;
	transient public static final byte SDT = 55;
	transient public static final byte BAT = 56;
	transient public static final byte EIS = 57;
	transient public static final byte TDT = 58;
	transient public static final byte TOT = 59;
	transient public static final byte MGT = 60;
	transient public static final byte TVCT = 61;
	transient public static final byte EIT = 62;
	transient public static final byte ETT = 63;
	transient public static final byte STT = 64;
	
	static public boolean isAudio(byte frameType){
		return frameType==TSPacket.AUDIO;
	}
	static public boolean isVideo(byte frameType){
		return frameType>=1 && frameType<=49;
	}
	/*static public boolean isVideoMpeg2(byte frameType){
		return frameType>=1 && frameType<=4;
	}
	static public boolean isVideoH264(byte frameType){
		return frameType>=5 && frameType<=6;
	}*/

	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		this.payloadUnitStart = in.readBoolean();
		this.pid = in.readShort();
		this.frameType = in.readByte();
		this.tsPacketByte = new byte[188];
		in.readFully(tsPacketByte);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeBoolean(payloadUnitStart);
	    out.writeShort(pid);
	    out.writeByte(frameType);
	    out.write(tsPacketByte);
	    out.flush();
	}
    
}
