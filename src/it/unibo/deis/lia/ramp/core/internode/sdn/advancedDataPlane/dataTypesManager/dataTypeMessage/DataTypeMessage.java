package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.dataTypeMessage;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This class will be used in ControllerMessageUpdate when the ControllerService
 * will send new data types to all the ControllerClients available.
 */
public class DataTypeMessage implements Serializable {
    private static final long serialVersionUID = -6324971373742497623L;

    private String fileName;

    private String className;

    private byte[] file;

    public DataTypeMessage(String fileName, String className, byte[] file) {
        this.fileName = fileName;
        this.className = className;
        this.file = file;
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

    public void setFile(byte[] file) {
        this.file = file;
    }

    public byte[] getFile() {
        return file;
    }
}
