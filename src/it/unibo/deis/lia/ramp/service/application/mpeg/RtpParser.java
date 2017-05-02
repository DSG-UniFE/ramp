/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

//import it.unibo.deis.lia.ramp.*;

//import java.io.*;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

import java.net.*;
//import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author useruser
 */
public class RtpParser extends AbstractRtpParser{

    private ArrayBlockingQueue<RTP> queue;
    private boolean active = true;

    private int startingTimeout;    // seconds
    private int workingTimeout;     // seconds
    //private int queueTimeout;       // seconds

    private DatagramSocket ds;
    public RtpParser(DatagramSocket ds)  throws Exception{
        this.ds = ds;
        this.queue = new ArrayBlockingQueue<RTP>(100);
        this.startingTimeout = 15;
        this.workingTimeout = 3;
        //queueTimeout = startingTimeout;
    }
    public RtpParser(DatagramSocket ds, int startingTimeout, int workingTimeout)  throws Exception{
        this.ds = ds;
        this.queue = new ArrayBlockingQueue<RTP>(100);
        this.startingTimeout = startingTimeout;
        this.workingTimeout = workingTimeout;
        //queueTimeout = startingTimeout;
    }
	
	@Override
    public RTP getRtp() throws Exception{
        RTP res = queue.poll(startingTimeout, TimeUnit.SECONDS);
        //queueTimeout = this.workingTimeout;
        //queueTimeout = 2;
        /*if(res==null){
            throw new EndOfStreamException();
        }*/
        return res;
    }

    @Override
    public void stopRtpMpegParser() {
        System.out.println("RtpMpegTsParser STOP");
        queue.add(new RTP(null));
        active = false;
    }
    
    @Override
    public void run(){

        /*Vector<Long> times = null;
        if(RampEntryPoint.logging){
            times = new Vector<Long>();
        }*/
        
        try{
            /*byte[] rtpHeader = null;
            while(rtpHeader==null){
                rtpHeader = getRtpHeader();
            }*/
        	//DatagramSocket ds = new DatagramSocket(port);
        	ds.setSoTimeout(startingTimeout*1000);
        	byte[] buffer = new byte[GenericPacket.MAX_UDP_PACKET];
        	DatagramPacket dp = new DatagramPacket(buffer,buffer.length);
            
            while(active){
                //System.out.println("\nRtpMpegTsParser NEW RTP");
            	ds.receive(dp);
            	ds.setSoTimeout(workingTimeout*1000);
            	int cc = dp.getData()[0] & 15;
            	//System.out.println("RtpParser.getRtpHeader dp.getLength() "+dp.getLength());
                //System.out.println("RtpParser.getRtpHeader cc "+cc);
                byte[] rtpHeader = new byte[12+4*cc];
                //System.out.println("RtpParser.getRtpHeader rtpHeader.length "+rtpHeader.length);
                System.arraycopy(dp.getData(), 0, rtpHeader, 0, rtpHeader.length);

                //System.out.println("RtpParser.getRtpHeader dp.getLength() "+dp.getLength());
                byte[] rtpPayload = new byte[dp.getLength()-rtpHeader.length];
                //System.out.println("RtpParser.getRtpHeader rtpPayload.length "+rtpPayload.length);
                System.arraycopy(dp.getData(), rtpHeader.length, rtpPayload, 0, dp.getLength()-rtpHeader.length);
                
                RTP rtp = new RTP(rtpHeader, rtpPayload);
                
                if(queue.remainingCapacity()==0){
                    queue.remove();
                    System.out.println("RtpParser dropping old RTP packet due to full queue");
                }
                queue.add(rtp);
            }// end while
        }
        //catch(EndOfStreamException eofe){
            //eofe.printStackTrace();
            //System.out.println("RtpMpegTsParser EndOfStreamException");
        //}
        catch(Exception e){
            e.printStackTrace();
        }

        /*if(RampEntryPoint.logging){
            if(times != null){
                if(times.size()>0) times.removeElementAt(0);
                if(times.size()>0) times.removeElementAt(0);
                if(times.size()>0) times.removeElementAt(0);
            }

            Collections.sort(times);
            Long[] elapsedArray = times.toArray(new Long[0]);

            try{
                FileWriter fw = new FileWriter("./temp/parsing.csv");
                fw.write("Parsing time (ms)\n");
                for(int i=0; i<elapsedArray.length; i++){
                    fw.write(""+elapsedArray[i]/(1000.0F)/(1000.0F)+"\n");
                }
                fw.close();
            }
            catch(Exception e){
                e.printStackTrace();
            }

            int maxValue = Math.round(times.size()*0.95F);
            float countMean = 0;
            for(int i=0; i<maxValue; i++){
                countMean += elapsedArray[i];
                //System.out.println("RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            }
            float mean = countMean/((float)maxValue);

            //for(int i=maxValue; i<elapsedArray.length; i++){
            //    System.out.println("DISCARDING RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            //}
            
            float countStddev = 0;
            for(int i=0; i<maxValue; i++){
                float x = elapsedArray[i] - mean;
                countStddev += ( x * x );
            }
            float y = countStddev/((float)maxValue);
            float stddev = (float)Math.sqrt(y);
            System.out.println(
                    "95% RtpMpegTsParser mean (ms): "+mean/(1000.0F)/(1000.0F)+"   " +
                    "stddev (ms): "+stddev/(1000.0F)/(1000.0F)+"   " +
                    "elapsedArray.length "+maxValue+"   " +
                    "rtpTsPackets "+rtpTsPackets);


            maxValue = Math.round(times.size()*0.90F);
            //elapsedArray = times.toArray(new Long[0]);
            countMean = 0;
            for(int i=0; i<maxValue; i++){
                countMean += elapsedArray[i];
                //System.out.println("RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            }
            mean = countMean/((float)maxValue);

            //for(int i=maxValue; i<elapsedArray.length; i++){
            //    System.out.println("DISCARDING RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            //}

            countStddev = 0;
            for(int i=0; i<maxValue; i++){
                float x = elapsedArray[i] - mean;
                countStddev += ( x * x );
            }
            y = countStddev/((float)maxValue);
            stddev = (float)Math.sqrt(y);
            System.out.println(
                    "90% RtpMpegTsParser mean (ms): "+mean/(1000.0F)/(1000.0F)+"   " +
                    "stddev (ms): "+stddev/(1000.0F)/(1000.0F)+"   " +
                    "elapsedArray.length "+maxValue+"   " +
                    "rtpTsPackets "+rtpTsPackets);
        }*/

        System.out.println("RtpParser FINISHED");
    }
    
}
