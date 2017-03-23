/*****************************************************************************
 *                                                                           *
 *  This file is part of the Dragon Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is Dragon. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package com.dragon.lang.classpath;

import com.dragon.lang.*;
import com.dragon.lang.classpath.DragonClassPath.ClassSource;
import com.dragon.lang.classpath.DragonClassPath.GeneratedClassSource;
import com.dragon.lang.classpath.DragonClassPath.JarClassSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import com.dragon.lang.utils.*;

/**
 * <pre>
 * Manage all classloading in Dragon.
 * Allows classpath extension and class file reloading.
 *
 * This class holds the implementation of the DragonClassManager so that it
 * can be separated from the lang package.
 *
 * This class currently relies on 1.2 for DragonClassLoader and weak references.
 * Is there a workaround for weak refs?  If so we could make this work
 * with 1.1 by supplying our own classloader code...
 *
 * See "http://www.beanshell.org/manual/classloading.html" for details
 * on the com.dragon.lang classloader architecture.
 *
 * Dragon has a multi-tiered class loading architecture.  No class loader is
 * created unless/until a class is generated, the classpath is modified,
 * or a class is reloaded.
 *
 * Note: we may need some synchronization in here
 *
 * Note on jdk1.2 dependency:
 *
 * We are forced to use weak references here to accomodate all of the
 * fleeting namespace listeners.  (NameSpaces must be informed if the class
 * space changes so that they can un-cache names).  I had the interesting
 * thought that a way around this would be to implement Dragon's own
 * garbage collector...  Then I came to my senses and said - screw it,
 * class re-loading will require 1.2.
 *
 * ---------------------
 *
 * Classloading precedence:
 *
 * in-script evaluated class (scripted class)
 * in-script added / modified classpath
 *
 * optionally, external classloader
 * optionally, thread context classloader
 *
 * plain Class.forName()
 * source class (.java file in classpath)
 *
 * </pre>
 */
public class ClassManagerImpl extends DragonClassManager {
    static final String DRAGON_PACKAGE = "com.dragon.lang";
    /**
     * The classpath of the base loader.  Initially and upon reset() this is
     * an empty instance of DragonClassPath.  It grows as paths are added or is
     * reset when the classpath is explicitly set.  This could also be called
     * the "extension" class path, but is not strictly confined to added path
     * (could be set arbitrarily by setClassPath())
     */
    private DragonClassPath baseClassPath;
    private boolean superImport;

    /**
     * This is the full blown classpath including baseClassPath (extensions),
     * user path, and java bootstrap path (rt.jar)
     * <p>
     * This is lazily constructed and further (and more importantly) lazily
     * intialized in components because mapping the full path could be
     * expensive.
     * <p>
     * The full class path is a composite of:
     * baseClassPath (user extension) : userClassPath : bootClassPath
     * in that order.
     */
    private DragonClassPath fullClassPath;

    // ClassPath Change listeners
    private Vector listeners = new Vector();
    private ReferenceQueue refQueue = new ReferenceQueue();

    /**
     * This handles extension / modification of the base classpath
     * The loader to use where no mapping of reloaded classes exists.
     * <p>
     * The baseLoader is initially null meaning no class loader is used.
     */
    private DragonClassLoader baseLoader;

    /**
     * Map by classname of loaders to use for reloaded classes
     */
    private Map loaderMap;

    /**
     * Used by DragonClassManager singleton constructor
     */
    public ClassManagerImpl() {
        reset();
    }

    /**
     * @return the class or null
     */
    @Override
    public Class classForName(String name) {
        // check positive cache
        Class c = absoluteClassCache.get(name);
        if (c != null)
            return c;

        // check negative cache
        if (absoluteNonClasses.contains(name)) {
            if (DragonBasicInterpreter.DEBUG) DragonBasicInterpreter.debug("absoluteNonClass list hit: " + name);
            return null;
        }

        if (DragonBasicInterpreter.DEBUG) DragonBasicInterpreter.debug("Trying to load class: " + name);

        // Check explicitly mapped (reloaded) class...
        final ClassLoader overlayLoader = getLoaderForClass(name);
        if (overlayLoader != null) {
            try {
                c = overlayLoader.loadClass(name);
            } catch (Exception e) {
                if (DragonBasicInterpreter.DEBUG)
                    DragonBasicInterpreter.debug("overlay loader failed for '" + name + "' - " + e);
            }
            // Should be there since it was explicitly mapped
            // throw an error if c == null)?
        }

        // insure that lang classes are loaded from the same loader
        if ((c == null) && name.startsWith(DRAGON_PACKAGE)) {
            final ClassLoader myClassLoader = DragonBasicInterpreter.class.getClassLoader(); // is null if located in bootclasspath
            if (myClassLoader != null) {
                try {
                    c = myClassLoader.loadClass(name);
                } catch (ClassNotFoundException e) {
                    // fall through
                } catch (NoClassDefFoundError e) {
                    // fall through
                }
            } else {
                try {
                    c = Class.forName(name);
                } catch (ClassNotFoundException e) {
                    // fall through
                } catch (NoClassDefFoundError e) {
                    // fall through
                }
            }
        }

        // Check classpath extension / reloaded classes
        if ((c == null) && (baseLoader != null)) {
            try {
                c = baseLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                // fall through
            }
        }

        // Optionally try external classloader
        if ((c == null) && (externalClassLoader != null)) {
            try {
                c = externalClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                // fall through
            }
        }

        // Optionally try context classloader
        // Note that this might be a security violation
        // is catching the SecurityException sufficient for all environments?
        // or do we need a way to turn this off completely?
        if (c == null) {
            try {
                final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                if (contextClassLoader != null) {
                    c = Class.forName(name, true, contextClassLoader);
                }
            } catch (ClassNotFoundException e) {
                // fall through
            } catch (NoClassDefFoundError e) {
                // fall through
            } catch (SecurityException e) {
                // fall through
            }
        }

        // try plain class forName()
        if (c == null)
            try {
                c = Class.forName(name);
            } catch (ClassNotFoundException e) {
                // fall through
/* I disagree with letting this fall through  -fschmidt
            } catch ( NoClassDefFoundError e ) {
				// fall through
*/
            }

        // Cache result (or null for not found)
        cacheClassInfo(name, c);

        return c;
    }

    /**
     * Get a resource URL using the Dragon classpath
     *
     * @param path should be an absolute path
     */
    @Override
    public URL getResource(String path) {
        URL url = null;
        if (baseLoader != null)
            // classloader wants no leading slash
            url = baseLoader.getResource(path.substring(1));
        if (url == null)
            url = super.getResource(path);
        return url;
    }

    /**
     * Get a resource stream using the Dragon classpath
     *
     * @param path should be an absolute path
     */
    @Override
    public InputStream getResourceAsStream(String path) {
        InputStream in = null;
        if (baseLoader != null) {
            // classloader wants no leading slash
            in = baseLoader.getResourceAsStream(path.substring(1));
        }
        if (in == null) {
            in = super.getResourceAsStream(path);
        }
        return in;
    }

    ClassLoader getLoaderForClass(String name) {
        return (ClassLoader) loaderMap.get(name);
    }

    // Classpath mutators

    /**
     */
    @Override
    public void addClassPath(URL path)
            throws IOException {
        if (baseLoader == null)
            setClassPath(new URL[]{path});
        else {
            // opportunity here for listener in classpath
            baseLoader.addURL(path);
            baseClassPath.add(path);
            classLoaderChanged();
        }
    }

    /**
     * Clear all classloading behavior and class caches and reset to
     * initial state.
     */
    @Override
    public void reset() {
        baseClassPath = new DragonClassPath("baseClassPath");
        baseLoader = null;
        loaderMap = new HashMap();
        classLoaderChanged(); // calls clearCaches() for us.
    }

    /**
     * Set a new base classpath and create a new base classloader.
     * This means all types change.
     */
    @Override
    public void setClassPath(URL[] cp) {
        baseClassPath.setPath(cp);
        initBaseLoader();
        loaderMap = new HashMap();
        classLoaderChanged();
    }

    /**
     * Overlay the entire path with a new class loader.
     * Set the base path to the user path + base path.
     * <p>
     * No point in including the boot class path (can't reload thos).
     */
    @Override
    public void reloadAllClasses() throws ClassPathException {
        DragonClassPath bcp = new DragonClassPath("temp");
        bcp.addComponent(baseClassPath);
        bcp.addComponent(DragonClassPath.getUserClassPath());
        setClassPath(bcp.getPathComponents());
    }

    /**
     * init the baseLoader from the baseClassPath
     */
    private void initBaseLoader() {
        if (Capabilities.isAndroid()) {
            baseLoader = new DragonDexClassLoader(this, baseClassPath);
        } else {
            baseLoader = new DragonJavaClassLoader(this, baseClassPath);
        }
    }

    // class reloading

    /**
     * Reloading classes means creating a new classloader and using it
     * whenever we are asked for classes in the appropriate space.
     * For this we use a DiscreteFilesClassLoader
     */
    @Override
    public void reloadClasses(String[] classNames)
            throws ClassPathException {
        // validate that it is a class here?

        // init base class loader if there is none...
        if (baseLoader == null)
            initBaseLoader();

        DiscreteFilesClassLoader.ClassSourceMap map =
                new DiscreteFilesClassLoader.ClassSourceMap();

        for (int i = 0; i < classNames.length; i++) {
            String name = classNames[i];

            // look in baseLoader class path
            ClassSource classSource = baseClassPath.getClassSource(name);

            // look in user class path
            if (classSource == null) {
                DragonClassPath.getUserClassPath().insureInitialized();
                classSource = DragonClassPath.getUserClassPath().getClassSource(
                        name);
            }

            // No point in checking boot class path, can't reload those.
            // else we could have used fullClassPath above.

            if (classSource == null)
                throw new ClassPathException("Nothing known about class: "
                        + name);

            // JarClassSource is not working... just need to implement it's
            // getCode() method or, if we decide to, allow the DragonClassManager
            // to handle it... since it is a URLClassLoader and can handle JARs
            if (classSource instanceof JarClassSource)
                throw new ClassPathException("Cannot reload class: " + name +
                        " from source: " + classSource);

            map.put(name, classSource);
        }

        // Create classloader for the set of classes
        ClassLoader cl = new DiscreteFilesClassLoader(this, map);

        // map those classes the loader in the overlay map
        Iterator it = map.keySet().iterator();
        while (it.hasNext())
            loaderMap.put(it.next(), cl);

        classLoaderChanged();
    }

    /**
     * Reload all classes in the specified package: e.g. "com.sun.tools"
     * <p>
     * The special package name "<unpackaged>" can be used to refer
     * to unpackaged classes.
     */
    @Override
    public void reloadPackage(String pack)
            throws ClassPathException {
        Collection classes =
                baseClassPath.getClassesForPackage(pack);

        if (classes == null)
            classes =
                    DragonClassPath.getUserClassPath().getClassesForPackage(pack);

        // no point in checking boot class path, can't reload those

        if (classes == null)
            throw new ClassPathException("No classes found for package: " + pack);

        reloadClasses((String[]) classes.toArray(new String[0]));
    }

    /**
     Unimplemented
     For this we'd have to store a map by location as well as name...

     public void reloadPathComponent( URL pc ) throws ClassPathException {
     throw new ClassPathException("Unimplemented!");
     }
     */

    // end reloading

    /**
     * Get the full blown classpath.
     */
    public DragonClassPath getClassPath() throws ClassPathException {
        if (fullClassPath != null)
            return fullClassPath;

        fullClassPath = new DragonClassPath("DragonInterpreter Full Class Path");
        fullClassPath.addComponent(DragonClassPath.getUserClassPath());
        try {
            fullClassPath.addComponent(DragonClassPath.getBootClassPath());
        } catch (ClassPathException e) {
            System.err.println("Warning: can't get boot class path");
        }
        fullClassPath.addComponent(baseClassPath);

        return fullClassPath;
    }

    /**
     * Support for "import *;"
     * Hide details in here as opposed to NameSpace.
     */
    @Override
    public void doSuperImport()
            throws UtilEvalException {
        // Should we prevent it from happening twice?

        try {
            getClassPath().insureInitialized();
            // prime the lookup table
            getClassNameByUnqName("");

            // always true now
            //getClassPath().setNameCompletionIncludeUnqNames(true);

        } catch (ClassPathException e) {
            throw new UtilEvalException("Error importing classpath " + e);
        }

        superImport = true;
    }

    @Override
    public boolean hasSuperImport() {
        return superImport;
    }

    /**
     * Return the name or null if none is found,
     * Throw an ClassPathException containing detail if name is ambigous.
     */
    @Override
    public String getClassNameByUnqName(String name)
            throws ClassPathException {
        return getClassPath().getClassNameByUnqName(name);
    }

    @Override
    public void addListener(Listener l) {
        listeners.addElement(new WeakReference(l, refQueue));

        // clean up old listeners
        Reference deadref;
        while ((deadref = refQueue.poll()) != null) {
            boolean ok = listeners.removeElement(deadref);
            if (ok) {
                //System.err.println("cleaned up weak ref: "+deadref);
            } else {
                if (DragonBasicInterpreter.DEBUG) DragonBasicInterpreter.debug(
                        "tried to remove non-existent weak ref: " + deadref);
            }
        }
    }

    @Override
    public void removeListener(Listener l) {
        throw new Error("unimplemented");
    }

    public ClassLoader getBaseLoader() {
        return baseLoader.getClassLoader();
    }

    /**
     * Get the Dragon classloader.
     * public ClassLoader getClassLoader() {
     * }
     */

	/*
        Impl Notes:
		We add the bytecode source and the "reload" the class, which causes the
		DragonClassLoader to be initialized and create a DiscreteFilesClassLoader
		for the bytecode.

		@exception ClassPathException can be thrown by reloadClasses
	*/
    @Override
    public Class defineClass(String name, byte[] code) {
        baseClassPath.setClassSource(name, new GeneratedClassSource(code));
        try {
            reloadClasses(new String[]{name});
        } catch (ClassPathException e) {
            throw new InterpreterException("defineClass: " + e);
        }
        return classForName(name);
    }

    /**
     * Clear global class cache and notify namespaces to clear their
     * class caches.
     * <p>
     * The listener list is implemented with weak references so that we
     * will not keep every namespace in existence forever.
     */
    @Override
    protected void classLoaderChanged() {
        // clear the static caches in DragonClassManager
        clearCaches();

        Vector toRemove = new Vector(); // safely remove
        for (Enumeration e = listeners.elements(); e.hasMoreElements(); ) {
            WeakReference wr = (WeakReference) e.nextElement();
            Listener l = (Listener) wr.get();
            if (l == null)  // garbage collected
                toRemove.add(wr);
            else
                l.classLoaderChanged();
        }
        for (Enumeration e = toRemove.elements(); e.hasMoreElements(); )
            listeners.removeElement(e.nextElement());
    }

    @Override
    public void dump(PrintWriter i) {
        i.println("Dragon Class Manager Dump: ");
        i.println("----------------------- ");
        i.println("baseLoader = " + baseLoader);
        i.println("loaderMap= " + loaderMap);
        i.println("----------------------- ");
        i.println("baseClassPath = " + baseClassPath);
    }

}
