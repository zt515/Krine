package com.krine.lang.classpath;

import com.krine.kar.KarFile;
import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.ast.Name;
import com.krine.lang.utils.Capabilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;

/**
 * KrineClassManager manages all classloading in Krine.
 * It also supports a dynamically loaded module (krine.classpath package)
 * which allows classpath module and class file reloading.
 * <p>
 * Currently the module relies on 1.2 for KrineClassLoader and weak
 * references.
 * <p>
 * Krine has a multi-tiered class loading architecture.  No class loader is
 * used unless/until the classpath is modified or a class is reloaded.
 * <p>
 */
/*
    Implementation notes:

	Note: we may need some synchronization in here

	Note on version dependency:  This base class is JDK 1.1 compatible,
	however we are forced to use weak references in the full featured
	implementation (the optional krine.classpath package) to accomodate all of
	the fleeting namespace listeners as they fall out of scope.  (NameSpaces
	must be informed if the class space changes so that they can un-cache
	names).  
	<p>

	Perhaps a simpler idea would be to have entities that reference cached
	types always perform a light weight check with a counter / reference
	value and use that to detect changes in the namespace.  This puts the 
	burden on the consumer to check at appropriate times, but could eliminate
	the need for the listener system in many places and the necessity of weak 
	references in this package.
	<p>
*/
public class KrineClassManager {
    /**
     * The krineBasicInterpreter which created the class manager
     * This is used to load scripted classes from source files.
     */
    private KrineBasicInterpreter declaringKrineBasicInterpreter;

    /**
     * An external classloader supplied by the setClassLoader() command.
     */
    protected ClassLoader externalClassLoader;

    /**
     * Global cache for things we know are classes.
     * Note: these should probably be re-implemented with Soft references.
     * (as opposed to strong or Weak)
     */
    protected transient Map<String, Class> absoluteClassCache = new Hashtable<>();
    /**
     * Global cache for things we know are *not* classes.
     * Note: these should probably be re-implemented with Soft references.
     * (as opposed to strong or Weak)
     */
    protected transient Set<String> absoluteNonClasses = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Caches for resolved object and static methods.
     * We keep these maps separate to support fast lookup in the general case
     * where the method may be either.
     */
    protected transient volatile Map<SignatureKey, Method> resolvedObjectMethods = new Hashtable<>();
    protected transient volatile Map<SignatureKey, Method> resolvedStaticMethods = new Hashtable<>();

    private transient Set<String> definingClasses = Collections.synchronizedSet(new HashSet<String>());
    protected transient Map<String, String> definingClassesBaseNames = new Hashtable<>();

    private static final Map<KrineClassManager, Object> classManagers = Collections.synchronizedMap(new WeakHashMap<KrineClassManager, Object>());

    public static void clearResolveCache() {
        KrineClassManager[] managers = classManagers.keySet().toArray(new KrineClassManager[0]);
        for (KrineClassManager m : managers) {
            m.resolvedObjectMethods = new Hashtable<>();
            m.resolvedStaticMethods = new Hashtable<>();
        }
    }

    /**
     * Create a new instance of the class manager.
     * Class manager instances are now associated with the krineBasicInterpreter.
     *
     * @see KrineBasicInterpreter#getClassManager()
     * @see KrineBasicInterpreter#setClassLoader(ClassLoader)
     */
    public static KrineClassManager createClassManager(KrineBasicInterpreter krineBasicInterpreter) {
        KrineClassManager manager;

        // Do we have the optional package?
        if (Capabilities.classExists("com.krine.lang.classpath.ClassManagerImpl"))
            try {
                // Try to load the module
                // don't refer to it directly here or we're dependent upon it
                Class clazz = Class.forName("com.krine.lang.classpath.ClassManagerImpl");
                manager = (KrineClassManager) clazz.newInstance();
            } catch (Exception e) {
                throw new InterpreterException("Error loading ClassManager", e);
            }
        else
            manager = new KrineClassManager();

        if (krineBasicInterpreter == null)
            krineBasicInterpreter = new KrineBasicInterpreter();
        manager.declaringKrineBasicInterpreter = krineBasicInterpreter;
        classManagers.put(manager, null);
        return manager;
    }

    public boolean classExists(String name) {
        return (classForName(name) != null);
    }

    /**
     * Load the specified class by name, taking into account added classpath
     * and reloaded classes, etc.
     * Note: Again, this is just a trivial implementation.
     * See krine.classpath.ClassManagerImpl for the fully functional class
     * management package.
     *
     * @return the class or null
     */
    public Class classForName(String name) {
        if (isClassBeingDefined(name))
            throw new InterpreterException(
                    "Attempting to load class in the process of being defined: "
                            + name);

        Class clazz = null;
        try {
            clazz = plainClassForName(name);
        } catch (ClassNotFoundException e) { /*ignore*/ }

        return clazz;
    }

    /**
     * Perform a plain Class.forName() or call the externally provided
     * classloader.
     * If a KrineClassManager implementation is loaded the call will be
     * delegated to it, to allow for additional hooks.
     * <p/>
     * <p>
     * This simply wraps that bottom level class lookup call and provides a
     * central point for monitoring and handling certain Java version
     * dependent bugs, etc.
     *
     * @return the class
     * @see #classForName(String)
     */
    public Class plainClassForName(String name)
            throws ClassNotFoundException {
        Class c = null;

        if (externalClassLoader != null)
            c = externalClassLoader.loadClass(name);
        else
            c = Class.forName(name);

        cacheClassInfo(name, c);

        return c;
    }

    /**
     * Get a resource URL using the Krine classpath
     *
     * @param path should be an absolute path
     */
    public URL getResource(String path) {
        URL url = null;
        if (externalClassLoader != null) {
            // classloader wants no leading slash
            url = externalClassLoader.getResource(path.substring(1));
        }
        if (url == null)
            url = KrineBasicInterpreter.class.getResource(path);

        return url;
    }

    /**
     * Get a resource stream using the Krine classpath
     *
     * @param path should be an absolute path
     */
    public InputStream getResourceAsStream(String path) {
        InputStream in = null;
        if (externalClassLoader != null) {
            // classloader wants no leading slash
            in = externalClassLoader.getResourceAsStream(path.substring(1));
        }
        if (in == null)
            in = KrineBasicInterpreter.class.getResourceAsStream(path);

        return in;
    }

    /**
     * Cache info about whether name is a class or not.
     *
     * @param value if value is non-null, cache the class
     *              if value is null, set the flag that it is *not* a class to
     *              speed later resolution
     */
    public void cacheClassInfo(String name, Class value) {
        if (value != null)
            absoluteClassCache.put(name, value);
        else
            absoluteNonClasses.add(name);
    }

    /**
     * Cache a resolved (possibly overloaded) method based on the
     * argument types used to invoke it, subject to classloader change.
     * Static and Object methods are cached separately to support fast lookup
     * in the general case where either will do.
     */
    public void cacheResolvedMethod(
            Class clazz, Class[] types, Method method) {
        if (KrineBasicInterpreter.DEBUG)
            KrineBasicInterpreter.debug(
                    "cacheResolvedMethod putting: " + clazz + " " + method);

        SignatureKey sk = new SignatureKey(clazz, method.getName(), types);
        if (Modifier.isStatic(method.getModifiers()))
            resolvedStaticMethods.put(sk, method);
        else
            resolvedObjectMethods.put(sk, method);
    }

    /**
     * Return a previously cached resolved method.
     *
     * @param onlyStatic specifies that only a static method may be returned.
     * @return the Method or null
     */
    public Method getResolvedMethod(
            Class clazz, String methodName, Class[] types, boolean onlyStatic) {
        SignatureKey sk = new SignatureKey(clazz, methodName, types);

        // Try static and then object, if allowed
        // Note that the Java compiler should not allow both.
        Method method = resolvedStaticMethods.get(sk);
        if (method == null && !onlyStatic)
            method = resolvedObjectMethods.get(sk);

        if (KrineBasicInterpreter.DEBUG) {
            if (method == null)
                KrineBasicInterpreter.debug(
                        "getResolvedMethod cache MISS: " + clazz + " - " + methodName);
            else
                KrineBasicInterpreter.debug(
                        "getResolvedMethod cache HIT: " + clazz + " - " + method);
        }
        return method;
    }

    /**
     * Clear the caches in KrineClassManager
     *
     * @see public void #reset() for external usage
     */
    protected void clearCaches() {
        absoluteNonClasses = Collections.synchronizedSet(new HashSet<String>());
        absoluteClassCache = new Hashtable<>();
        resolvedObjectMethods = new Hashtable<>();
        resolvedStaticMethods = new Hashtable<>();
    }

    /**
     * Set an external class loader.  Krine will use this at the same
     * point it would otherwise use the plain Class.forName().
     * i.e. if no explicit classpath management is done from the script
     * (addClassPath(), setClassPath(), reloadClasses()) then Krine will
     * only use the supplied classloader.  If additional classpath management
     * is done then Krine will perform that in addition to the supplied
     * external classloader.
     * However Krine is not currently able to reload
     * classes supplied through the external classloader.
     */
    public void setClassLoader(ClassLoader externalCL) {
        externalClassLoader = externalCL;
        classLoaderChanged();
    }

    public void addClassPath(URL path)
            throws IOException {
    }

    public void addClassPath(KarFile karFile)
            throws IOException {
    }

    /**
     * Clear all loaders and start over.  No class loading.
     */
    public void reset() {
        clearCaches();
    }

    /**
     * Set a new base classpath and create a new base classloader.
     * This means all types change.
     */
    public void setClassPath(URL[] cp)
            throws UtilEvalException {
        throw cmUnavailable();
    }

    /**
     * Overlay the entire path with a new class loader.
     * Set the base path to the user path + base path.
     * <p>
     * No point in including the boot class path (can't reload those).
     */
    public void reloadAllClasses() throws UtilEvalException {
        throw cmUnavailable();
    }

    /**
     * Reloading classes means creating a new classloader and using it
     * whenever we are asked for classes in the appropriate space.
     * For this we use a DiscreteFilesClassLoader
     */
    public void reloadClasses(String[] classNames)
            throws UtilEvalException {
        throw cmUnavailable();
    }

    /**
     * Reload all classes in the specified package: e.g. "com.sun.tools"
     * <p>
     * The special package name "<un-packaged>" can be used to refer
     * to un-packaged classes.
     */
    public void reloadPackage(String pack)
            throws UtilEvalException {
        throw cmUnavailable();
    }

    /**
     This has been removed from the interface to shield the lang from the
     rest of the classpath package. If you need the classpath you will have
     to cast the ClassManager to its impl.

     public KrineClassPath getClassPath() throws ClassPathException;
     */

    /**
     * Support for "import *;"
     * Hide details in here as opposed to NameSpace.
     */
    public void doSuperImport()
            throws UtilEvalException {
        throw cmUnavailable();
    }

    /**
     * A "super import" ("import *") operation has been performed.
     */
    public boolean hasSuperImport() {
        return false;
    }

    /**
     * Return the name or null if none is found,
     * Throw an ClassPathException containing detail if name is ambiguous.
     */
    public String getClassNameByUnqName(String name)
            throws UtilEvalException {
        throw cmUnavailable();
    }

    public void addListener(Listener l) {
    }

    public void removeListener(Listener l) {
    }

    public void dump(PrintWriter pw) {
        pw.println("KrineClassManager: no class manager.");
    }

    /**
     * Flag the class name as being in the process of being defined.
     * The class manager will not attempt to load it.
     */
    /*
        Note: this implementation is temporary. We currently keep a flat
		namespace of the base name of classes.  i.e. Krine cannot be in the
		process of defining two classes in different packages with the same
		base name.  To remove this limitation requires that we work through
		namespace imports in an analogous (or using the same path) as regular
		class import resolution.  This workaround should handle most cases 
		so we'll try it for now.
	*/
    public void definingClass(String className) {
        String baseName = Name.suffix(className, 1);
        int i = baseName.indexOf("$");
        if (i != -1)
            baseName = baseName.substring(i + 1);
        String cur = definingClassesBaseNames.get(baseName);
        if (cur != null)
            throw new InterpreterException("Defining class problem: " + className
                    + ": Krine cannot yet simultaneously define two or more "
                    + "dependant classes of the same name.  Attempt to define: "
                    + className + " while defining: " + cur
            );
        definingClasses.add(className);
        definingClassesBaseNames.put(baseName, className);
    }

    protected boolean isClassBeingDefined(String className) {
        return definingClasses.contains(className);
    }

    /**
     * This method is a temporary workaround used with definingClass.
     * It is to be removed at some point.
     */
    public String getClassBeingDefined(String className) {
        String baseName = Name.suffix(className, 1);
        return definingClassesBaseNames.get(baseName);
    }

    /**
     * Indicate that the specified class name has been defined and may be
     * loaded normally.
     */
    public void doneDefiningClass(String className) {
        String baseName = Name.suffix(className, 1);
        definingClasses.remove(className);
        definingClassesBaseNames.remove(baseName);
    }

    /*
        The real implementation in the classpath.ClassManagerImpl handles
        reloading of the generated classes.
    */
    public Class defineClass(String name, byte[] code) {
        throw new InterpreterException("Can't create class (" + name
                + ") without class manager package.");
	/*
		Old implementation injected classes into the parent classloader.
		This was incorrect behavior for several reasons.  The biggest problem
		is that classes could therefore only be defined once across all
		executions of the script...  

		ClassLoader cl = this.getClass().getClassLoader();
		Class clazz;
		try {
			clazz = (Class)Reflect.invokeObjectMethod(
				cl, "defineClass", 
				new Object [] { 
					name, code, 
					new Primitive( (int)0 )/offset/, 
					new Primitive( code.length )/len/ 
				}, 
				(KrineInterpreter)null, (CallStack)null, (SimpleNode)null
			);
		} catch ( Exception e ) {
			e.printStackTrace();
			throw new InterpreterException("Unable to define class: "+ e );
		}
		absoluteNonClasses.remove( name ); // may have been axed previously
		return clazz;
	*/
    }

    protected void classLoaderChanged() {
    }

    protected static UtilEvalException cmUnavailable() {
        return new Capabilities.Unavailable(
                "ClassLoading features unavailable.");
    }

    public interface Listener {
        void classLoaderChanged();
    }

    /**
     * SignatureKey serves as a hash of a method signature on a class
     * for fast lookup of overloaded and general resolved Java methods.
     * <p>
     */
	/*
		Note: is using SignatureKey in this way dangerous?  In the pathological
		case a user could eat up memory caching every possible combination of
		argument types to an untyped method.  Maybe we could be smarter about
		it by ignoring the types of untyped parameter positions?  The method
		resolver could return a set of "hints" for the signature key caching?

		There is also the overhead of creating one of these for every method
		dispatched.  What is the alternative?
	*/
    static class SignatureKey {
        Class clazz;
        Class[] types;
        String methodName;
        int hashCode = 0;

        SignatureKey(Class clazz, String methodName, Class[] types) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.types = types;
        }

        public int hashCode() {
            if (hashCode == 0) {
                hashCode = clazz.hashCode() * methodName.hashCode();
                if (types == null) // no args method
                    return hashCode;
                for (int i = 0; i < types.length; i++) {
                    int hc = types[i] == null ? 21 : types[i].hashCode();
                    hashCode = hashCode * (i + 1) + hc;
                }
            }
            return hashCode;
        }

        public boolean equals(Object o) {
            if (!(o instanceof SignatureKey)) {
                return false;
            }

            SignatureKey target = (SignatureKey) o;
            if (types == null)
                return target.types == null;
            if (clazz != target.clazz)
                return false;
            if (!methodName.equals(target.methodName))
                return false;
            if (types.length != target.types.length)
                return false;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == null) {
                    if (!(target.types[i] == null))
                        return false;
                } else if (!types[i].equals(target.types[i]))
                    return false;
            }

            return true;
        }
    }
}
