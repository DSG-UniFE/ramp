package it.unibo.deis.lia.ramp.util.rampClassLoader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * ClassLoader in charge of dynamically adding folders/jar files at runtime
 * and loading classes in such folders/jar files.
 *
 * This class loader is initialized at startup time by RampEntryPoint and in order to work
 * must have the SystemClassLoader as parent. See RampEntryPoint.rampClassLoader property.
 */
public class RampClassLoader extends URLClassLoader {

    public RampClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * Add folder/jar file at runtime to classpath
     * @param url of the folder/jar file
     */
    public void addURL(URL url) {
        super.addURL(url);
    }

    /**
     * Add folder/jar file at runtime to classpath
     * @param path of the folder/jar file in terms of String
     */
    public void addPath(String path) {
        File f = new File(path);
        URL url = null;
        try {
            url = f.toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        addURL(url);
    }
}
