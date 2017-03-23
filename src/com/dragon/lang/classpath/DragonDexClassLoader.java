package com.dragon.lang.classpath;
import java.net.*;
import java.io.*;


/**
 * ClassLoader for Android Dalvik class file
 */
public class DragonDexClassLoader implements DragonClassLoader
{
    
    public DragonDexClassLoader(DragonClassManager classManager, DragonClassPath bcp) {
        
    }

    @Override
    public ClassLoader getClassLoader()
    {
        // TODO: Implement this method
        return null;
    }

    @Override
    public void addURL(URL path)
    {
        // TODO: Implement this method
    }

    @Override
    public InputStream getResourceAsStream(String substring)
    {
        // TODO: Implement this method
        return null;
    }

    @Override
    public URL getResource(String substring)
    {
        // TODO: Implement this method
        return null;
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException
    {
        // TODO: Implement this method
        return null;
    }
    
}
