/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

import java.util.concurrent.*;

/**
 *
 * @author useruser
 */
public class SimpleRtpMpegTsParser extends AbstractRtpParser{

    private ArrayBlockingQueue<RTP> queue;

    public SimpleRtpMpegTsParser(){
        this.queue = new ArrayBlockingQueue<RTP>(50);
    }

    public void addRtp(RTP rtp){
        //queue.offer(rtp, 1, TimeUnit.SECONDS);
        queue.add(rtp);
    }

    private int queueTimeout = 15; // seconds
    @Override
    public RTP getRtp() throws Exception {
        RTP res = queue.poll(queueTimeout, TimeUnit.SECONDS);
        queueTimeout = 2;
        /*if(res==null){
            throw new EndOfStreamException();
        }*/
        return res;
    }

    @Override
    public void stopRtpMpegParser() {
        System.out.println("SimpleRtpMpegTsParser STOP");
        queue.add(new RTP(null));
    }
    
}
