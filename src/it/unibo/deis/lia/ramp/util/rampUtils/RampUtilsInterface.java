package it.unibo.deis.lia.ramp.util.rampUtils;

import java.util.Vector;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * Interface that exposes useful methods to be used
 * by components that need it
 *
 * The class that implements these methods should be retrieved
 * using the ComponentLocator object.
 *
 * Example of usage:
 * RampUtilsInterface rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));
 */
public interface RampUtilsInterface {
    /**
     * @return the local Ramp Id
     */
    int getNodeId();

    /**
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value from this random number generator's sequence
     */
    int nextRandomInt();

    /**
     * @param bound the upper bound (exclusive).  Must be positive.
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value between zero (inclusive) and {@code bound} (exclusive)
     *         from this random number generator's sequence
     */
    int nextRandomInt(int bound);

    /**
     * @return the list of all IP addresses assigned to all the local
     *         network interfaces.
     */
    Vector<String> getLocalNetworkAddresses();

    /**
     * @param bytes of the object to deserialize
     * @return the Object
     */
    Object deserialize(byte[] bytes) throws Exception;

    Object deserialize(byte[] bytes, int offset, int length) throws Exception;
}
