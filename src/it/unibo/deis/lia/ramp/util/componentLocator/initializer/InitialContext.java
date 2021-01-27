package it.unibo.deis.lia.ramp.util.componentLocator.initializer;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManager;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.cache.Cache;
import it.unibo.deis.lia.ramp.util.rampUtils.RampUtils;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * credits: https://www.baeldung.com/java-service-locator-pattern
 *
 * Creates references to components that will be stored in cache {@link Cache}
 * by ComponentLocator {@link ComponentLocator}
 *
 * This is the only static part of the solution, to add a new component
 * that can be retrieved at runtime you should add a new "else if" statement using as
 * identifier its classSimpleName.
 */
public class InitialContext {

    public Object lookup(String componentName) {
        if(componentName.equalsIgnoreCase("RampUtils")) {
            return RampUtils.getInstance();
        } else if(componentName.equalsIgnoreCase("ControllerClient")) {
            return ControllerClient.getInstance();
        } else if(componentName.equalsIgnoreCase("DataTypesManager")) {
            return DataTypesManager.getInstance();
        }
        return null;
    }
}
