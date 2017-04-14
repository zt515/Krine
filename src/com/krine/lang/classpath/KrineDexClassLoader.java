package com.krine.lang.classpath;

import java.io.InputStream;
import java.net.URL;


/**
 * ClassLoader for Android Dalvik class file
 */
public class KrineDexClassLoader implements KrineClassLoader {

    public KrineDexClassLoader(KrineClassManager classManager, KrineClassPath kcp) {

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
    public InputStream getResourceAsStream(String name) {
        return getClassLoader().getResourceAsStream(name);
    }

    @Override
    public URL getResource(String name) {
        return getClassLoader().getResource(name);
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        // TODO: Implement this method
        return null;
    }

}
