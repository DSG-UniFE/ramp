package it.unibo.deis.lia.ramp.util.rampUtils;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;

import java.util.Vector;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * Class with utils methods to be used with ComponentLocator
 */
public class RampUtils implements RampUtilsInterface {

    /**
     * RampUtils instance.
     */
    private static RampUtils rampUtils;

    private RampUtils() {

    }

    synchronized public static RampUtils getInstance() {
        if(rampUtils == null) {
            rampUtils = new RampUtils();
        }

        return rampUtils;
    }

    @Override
    public int getNodeId() {
        return Dispatcher.getLocalRampId();
    }

    @Override
    public int nextRandomInt() {
        return RampEntryPoint.nextRandomInt();
    }

    @Override
    public int nextRandomInt(int bound) {
        return RampEntryPoint.nextRandomInt(bound);
    }

    @Override
    public Vector<String> getLocalNetworkAddresses() {
        Vector<String> result = null;
        try {
            result = Dispatcher.getLocalNetworkAddresses();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public Object deserialize(byte[] bytes) throws Exception {
        return  E2EComm.deserialize(bytes);
    }

    @Override
    public Object deserialize(byte[] bytes, int offset, int length) throws Exception {
        return E2EComm.deserialize(bytes, offset, length);
    }
}
