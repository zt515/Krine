package com.dragon.lang.classpath;
import java.net.*;
import java.io.*;

public interface DragonClassLoader
{

    public ClassLoader getClassLoader();


    public void addURL(URL path);


    public InputStream getResourceAsStream(String substring);


    public URL getResource(String substring);


    public Class loadClass(String name) throws ClassNotFoundException;

    
}
