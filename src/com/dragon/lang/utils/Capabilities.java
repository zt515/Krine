package com.dragon.lang.utils;

import com.dragon.lang.UtilEvalException;
import com.dragon.lang.classpath.DragonClassManager;

import java.lang.reflect.AccessibleObject;
import java.util.Hashtable;

/**
 * The map of extended features supported by the runtime in which we live.
 * <p>
 * <p>
 * This class should be independent of all other dragon classes!
 * <p>
 * <p>
 * Note that tests for class existence here do *not* use the
 * DragonClassManager, as it may require other optional class files to be
 * loaded.
 */
public class Capabilities {
    private static volatile boolean accessibility = false;

    /**
     * If accessibility is enabled
     * determine if the accessibility mechanism exists and if we have
     * the optional dragon package to use it.
     * Note that even if both are true it does not necessarily mean that we
     * have runtime permission to access the fields... Java security has
     * a say in it.
     *
     * @see com.dragon.lang.reflect.Reflect
     */
    public static boolean haveAccessibility() {
        return accessibility;
    }

    public static void setAccessibility(boolean b) {
        if (!b) {
            accessibility = false;
        } else {
            String.class.getDeclaredMethods(); // test basic access
            try {
                final AccessibleObject member = String.class.getDeclaredField("value");
                member.setAccessible(true);
                member.setAccessible(false);
            } catch (NoSuchFieldException e) {
                // ignore
            }
            accessibility = true;
        }
        DragonClassManager.clearResolveCache();
    }

    private static Hashtable<Object, Object> classes = new Hashtable<>();

    /**
     * Use direct Class.forName() to test for the existence of a class.
     * We should not use DragonClassManager here because:
     * a) the systems using these tests would probably not load the
     * classes through it anyway.
     * b) dragonClassManager is heavy and touches other class files.
     * this capabilities code must be light enough to be used by any
     * system **including the remote applet**.
     */
    public static boolean classExists(String name) {
        Object c = classes.get(name);

        if (c == null) {
            try {
                c = Class.forName(name);
            } catch (ClassNotFoundException ignore) {
            }

            if (c != null)
                classes.put(c, "unused");
        }

        return c != null;
    }

    /**
     * An attempt was made to use an unavailable capability supported by
     * an optional package.  The normal operation is to test before attempting
     * to use these packages... so this is runtime exception.
     */
    public static class Unavailable extends UtilEvalException {
        public Unavailable(String s) {
            super(s);
        }
    }
}


