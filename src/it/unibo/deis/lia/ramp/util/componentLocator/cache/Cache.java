package it.unibo.deis.lia.ramp.util.componentLocator.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * credits: https://www.baeldung.com/java-service-locator-pattern
 *
 * This is an object for storing component references to reuse them later.
 */
public class Cache {
    private Map<String, Object> components = new ConcurrentHashMap<>();

    public Object getComponent(String componentName) {
        return components.get(componentName);
    }

    public void addComponent(Object object) {
        components.put(object.getClass().getSimpleName(), object);
    }
}
