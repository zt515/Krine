package krine.core;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.utils.LazySingleton;

import java.util.Properties;
import java.io.File;
import com.krine.lang.ast.This;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.ast.EvalError;
import java.io.IOException;

/**
 * This class provides Krine some system APIs.
 * such as system properties, environment variables, etc.
 *
 * @author Kiva
 * @date 2017/4/9
 */
@KrineAPI
@SuppressWarnings("unused")
public final class Core {
    private static final LazySingleton<Properties> PROPERTIES = new LazySingleton<Properties>() {
        @Override
        public Properties onCreate() {
            return new Properties();
        }
    };

    /**
     * Return the property by given name.
     *
     * @param name The property name.
     * @return Property value.
     */
    public static String getProperty(String name) {
        Properties map = PROPERTIES.get();
        return map.containsKey(name) ? map.getProperty(name) : null;
    }

    /**
     * Set property to given value.
     *
     * @param name     Property name.
     * @param property Property value.
     */
    public static void setProperty(String name, String property) {
        PROPERTIES.get().setProperty(name, property);
    }

    /**
     * Set multiple properties.
     * Only {key1, value1, key2, value2, ...} arrays are accepted.
     * Otherwise, do nothing.
     *
     * @param properties Properties to be set
     */
    public static void setProperties(String[] properties) {
        if (properties.length % 2 != 0) {
            return;
        }

        for (int i = 0; i < properties.length; ) {
            setProperty(properties[i++], properties[i++]);
        }
    }

    /**
     * Return the environment variable with given name.
     *
     * @param name Environment variable name
     * @return Environment variable.
     * @see System#getenv(String)
     */
    public static String getEnv(String name) {
        return System.getenv(name);
    }

    /**
     * Return current time in millisecond from 1970.
     *
     * @return Time in millisecond.
     * @see System#currentTimeMillis()
     */
    public static long getTime() {
        return System.currentTimeMillis();
    }
    
    /**
     * Change current working directory.
     *
     * @param dir New working directory.
     */
	public static void chdir(String dir) {
        System.setProperty("user.dir", dir);
	}
    
    /**
     * Load a file into current program.
     *
     * @param aThis Context info.
     * @param file  File to be loaded.
     * @return true if successful, otherwise false.
     * @see Core#load(This, File)
     */
    public static boolean load(This aThis, String file) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);
        if (interpreter == null) {
            return false;
        }

        try {
            interpreter.source(file);
        } catch (Exception e) {
            interpreter.println("Error loading file " + file + ": " + e.getLocalizedMessage());
        }
        return true;
    }
    
    /**
     * Load a file into current program.
     *
     * @param aThis Context info.
     * @param file  File to be loaded.
     * @return true if successful, otherwise false.
     * @see Core#load(This, String)
     */
    public static boolean load(This aThis, File file) {
        return Core.load(aThis, file.getAbsolutePath());
    }
}
