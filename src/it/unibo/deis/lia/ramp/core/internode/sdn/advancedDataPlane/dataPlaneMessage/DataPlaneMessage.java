package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataPlaneMessage;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This class will be used in ControllerMessageUpdate when the ControllerService
 * will send new DataTypes/DataPlaneRules to all the ControllerClients available.
 */
public class DataPlaneMessage implements Serializable {
    private static final long serialVersionUID = -6324971373742497623L;

    private String fileName;

    private String className;

    private byte[] classFile;

    public DataPlaneMessage(String fileName, String className, byte[] classFile) {
        this.fileName = fileName;
        this.className = className;
        this.classFile = classFile;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

    public void setClassFile(byte[] classFile) {
        this.classFile = classFile;
    }

    public byte[] getClassFile() {
        return classFile;
    }
}
