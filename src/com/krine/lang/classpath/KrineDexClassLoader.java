package com.krine.lang.classpath;

import java.io.InputStream;
import java.net.URL;


/**
 * ClassLoader for Android Dalvik class file
 */
public class KrineDexClassLoader implements KrineClassLoader {

    public KrineDexClassLoader(KrineClassManager classManager, KrineClassPath bcp) {

    }

    @Override
    public ClassLoader getClassLoader() {
        // TODO: Implement this method
        return null;
    }

    @Override
    public void addURL(URL path) {
        // TODO: Implement this method
    }

    @Override
    public InputStream getResourceAsStream(String substring) {
        // TODO: Implement this method
        return null;
    }

    @Override
    public URL getResource(String substring) {
        // TODO: Implement this method
        return null;
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        // TODO: Implement this method
        return null;
    }

}
