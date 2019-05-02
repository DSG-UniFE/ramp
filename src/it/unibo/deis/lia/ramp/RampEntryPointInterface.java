package it.unibo.deis.lia.ramp;

import it.unibo.deis.lia.ramp.util.rampClassLoader.RampClassLoader;

import java.util.ArrayList;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * TODO Check with Giannelli
 *
 * Not used at the moment.
 */
public interface RampEntryPointInterface {

    public void forceNeighborsUpdate();

    public String[] getCurrentNeighbors();

    public ArrayList<String> getClients();

    public ArrayList<String> getServices();

    public int getNodeId();

    public String getNodeIdString();

    public int nextRandomInt();

    public int nextRandomInt(int n);

    public float nextRandomFloat();

    public RampClassLoader getRampClassLoader();
}
