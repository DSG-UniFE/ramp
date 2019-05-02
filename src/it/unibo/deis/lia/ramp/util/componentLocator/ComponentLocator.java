package it.unibo.deis.lia.ramp.util.componentLocator;

import it.unibo.deis.lia.ramp.util.componentLocator.cache.Cache;
import it.unibo.deis.lia.ramp.util.componentLocator.initializer.InitialContext;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * credits: https://www.baeldung.com/java-service-locator-pattern
 *
 * The purpose of this compoent that follows the Service Locator pattern is to return
 * the component instances on demand.
 *
 * Example of usage:
 * ControllerClientInterface controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent("ControllerClient"));
 *
 * Future work:
 * To make this component more effective and safe the components method getInstance {@link InitialContext} should return an interface
 * instead of the object itself. To do that it is necessary to perform a deep code refactoring.
 */
public class ComponentLocator {

    private static Cache cache = new Cache();

    public static Object getComponent(ComponentType componentType) {
        Object component = cache.getComponent(componentType.getSimpleClassName());

        if(component != null) {
            return component;
        }

        InitialContext context = new InitialContext();
        component = context.lookup(componentType.getSimpleClassName());
        cache.addComponent(component);

        return component;
    }
}
