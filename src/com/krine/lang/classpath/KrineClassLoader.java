package com.krine.lang.classpath;

import java.io.InputStream;
import java.net.URL;

public interface KrineClassLoader {

    ClassLoader getClassLoader();


    void addURL(URL path);


    InputStream getResourceAsStream(String substring);


    URL getResource(String substring);


    Class loadClass(String name) throws ClassNotFoundException;


}
