/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 *
 * @author useruser
 */
public class UDPInputStream extends InputStream implements Runnable{

    private ArrayBlockingQueue<Byte> byteBuffer;
    private DatagramSocket ds;

    private int startingTimeout;
    private int workingTimeout;

    public UDPInputStream(int localPort) throws Exception{
        this.ds = new DatagramSocket(localPort);
        byteBuffer = new ArrayBlockingQueue<Byte>(1024*1024);
        this.startingTimeout = 15;
        this.workingTimeout = 3;
    }
    public UDPInputStream(int localPort, int startingTimeout, int workingTimeout) throws Exception{
        this.ds = new DatagramSocket(localPort);
        byteBuffer = new ArrayBlockingQueue<Byte>(1024*1024);
        this.startingTimeout = startingTimeout;
        this.workingTimeout = workingTimeout;
    }
    public UDPInputStream(DatagramSocket ds) throws Exception{
        this.ds = ds;
        byteBuffer = new ArrayBlockingQueue<Byte>(1024*1024);
        this.startingTimeout = 15;
        this.workingTimeout = 3;
    }
    public UDPInputStream(DatagramSocket ds, int startingTimeout, int workingTimeout) throws Exception{
        this.ds = ds;
        byteBuffer = new ArrayBlockingQueue<Byte>(1024*1024);
        this.startingTimeout = startingTimeout;
        this.workingTimeout = workingTimeout;
    }

    //private int timeout = 25;
    private boolean first = true;
    @Override
    public int read() throws IOException {
        int res;
        int timeout;
        if(first){
            timeout = this.startingTimeout;
            first = false;
        }
        else{
            timeout = this.workingTimeout;
        }
        try {
            //int res = (byteBuffer.take()+256)%256;
            Byte b = byteBuffer.poll(timeout, TimeUnit.SECONDS);
            if(b==null){
                res = -1;
                //System.out.println("UDPInputStream.read res = -1");
            }
            else{
                res = (b+256)%256;
            }
            
            //System.out.println("UDPInputStream.read res="+res+"  (byte)res="+(byte)res);
            return res;
        }
        //catch (InterruptedException ie) {
        catch (Exception e) {
            e.printStackTrace();
            res = -1;
        }
        return res;
    }

    @Override
    public int available() throws IOException {
        System.out.println("UDPInputStream.available "+byteBuffer.size());
        return byteBuffer.size();
    }
    
    @Override
    public void run(){
        try{
            boolean first = true;
            ds.setSoTimeout(this.startingTimeout*1000);
            byte[] packetBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
            DatagramPacket dp = new DatagramPacket(packetBuffer, packetBuffer.length);
            while(true){
                //System.out.println("UDPInputStream receiving... "+packetBuffer.length);
                ds.receive(dp);
                if(first){
                    ds.setSoTimeout(this.workingTimeout*1000);
                    first = false;
                }
                //System.out.println("UDPInputStream received "+dp.getLength());
                //buffer.put(packetBuffer, 0, packetBuffer.length);
                byte[] receivedBuffer = dp.getData();
                //total+=dp.getLength();
                for(int i=0; i<dp.getLength(); i++){
                    //System.out.print(receivedBuffer[i]+" "+((receivedBuffer[i]+256)%256)+"\t");
                    //byteBuffer.put((receivedBuffer[i]+256)%256);
                    byteBuffer.put(receivedBuffer[i]);
                }
                //System.out.println();
            }
        }
        /*catch(java.net.SocketTimeoutException ste){
            byteBuffer.put(-1);
        }*/
        catch(SocketTimeoutException ste){
            //ste.printStackTrace();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("UDPInputStream FINISHED");
    }
}
