package it.unibo.deis.lia.ramp.util.componentLocator;

/**
 * @author Dmitrij David Padalino Montenero
 */
public enum ComponentType {
    RAMP_ENTRY_POINT("RampEntryPoint"),
    CONTROLLER_CLIENT("ControllerClient"),
    DATA_TYPES_MANAGER("DataTypesManager");

    private String simpleClassName;

    ComponentType(String simpleClassName) {
        this.simpleClassName = simpleClassName;
    }

    public String getSimpleClassName() {
        return simpleClassName;
    }
}
