package com.krine.lang.ast;

import com.krine.lang.*;
import com.krine.lang.classpath.ClassIdentifier;
import com.krine.lang.classpath.KrineClassManager;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.utils.CallStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A namespace	in which methods, variables, and imports (class names) live.
 * This is package public because it is used in the implementation of some
 * com.krine.lang commands.  However for normal use you should be using methods on
 * com.krine.lang.KrineInterpreter to interact with your scripts.
 * <p>
 * <p>
 * A com.krine.lang.ast.This object is a thin layer over a NameSpace that associates it with
 * an KrineInterpreter instance.  Together they comprise a Krine scripted object
 * context.
 * <p>
 */
// not at all thread-safe - fschmidt
public class NameSpace implements Serializable, KrineClassManager.Listener, NameSource, Cloneable {

    private static final long serialVersionUID = 5004976946651004751L;

    public static final NameSpace JAVACODE =
            new NameSpace((KrineClassManager) null, "Called from compiled Java code.");

    static {
        JAVACODE.isMethod = true;
    }

    // Begin instance data
    // Note: if we add something here we should reset it in the clear() method.

    /**
     * The name of this namespace.  If the namespace is a method body
     * namespace then this is the name of the method.  If it's a class or
     * class instance then it's the name of the class.
     */
    private String nsName;
    private NameSpace parent;
    private Map<String, Variable> variables;
    private Map<String, List<KrineMethod>> methods;

    protected Map<String, String> importedClasses;
    private List<String> importedPackages;
    private List<String> importedCommands;
    private List<Object> importedObjects;
    private List<Class> importedStatic;
    private String packageName;

    transient private KrineClassManager classManager;

    // See notes in getThis()
    private This thisReference;

    /**
     * Name resolver objects
     */
    private Map<String, Name> names;

    /**
     * The node associated with the creation of this namespace.
     * This is used support getInvocationLine() and getInvocationText().
     */
    SimpleNode callerInfoNode;

    /**
     * Note that the namespace is a method body namespace.  This is used for
     * printing stack traces in exceptions.
     */
    boolean isMethod;
    /**
     * Note that the namespace is a class body or class instance namespace.
     * This is used for controlling static/object import precedence, etc.
     */
    /*
		Note: We will ll move this behavior out to a subclass of 
		NameSpace, but we'll start here.
	*/
    boolean isClass;
    Class classStatic;
    Object classInstance;

    void setClassStatic(Class clas) {
        this.classStatic = clas;
        importStatic(clas);
    }

    void setClassInstance(Object instance) {
        this.classInstance = instance;
        importObject(instance);
    }

    Object getClassInstance()
            throws UtilEvalException {
        if (classInstance != null)
            return classInstance;

        if (classStatic != null
            //|| ( getParent()!=null && getParent().classStatic != null )
                )
            throw new UtilEvalException(
                    "Can't refer to class instance from static context.");
        else
            throw new InterpreterException(
                    "Can't resolve class instance 'this' in: " + this);
    }


    /**
     * Local class cache for classes resolved through this namespace using
     * getClass() (taking into account imports).  Only unqualified class names
     * are cached here (those which might be imported).  Qualified names are
     * always absolute and are cached by KrineClassManager.
     */
    transient private Map<String, Class> classCache;

    // End instance data

    // Begin constructors

    /**
     * @parent the parent namespace of this namespace.  Child namespaces
     * inherit all variables and methods of their parent and can (of course)
     * override / shadow them.
     */
    public NameSpace(NameSpace parent, String name) {
        // Note: in this case parent must have a class manager.
        this(parent, null, name);
    }

    public NameSpace(KrineClassManager classManager, String name) {
        this(null, classManager, name);
    }

    public NameSpace(
            NameSpace parent, KrineClassManager classManager, String name) {
        // We might want to do this here rather than explicitly in KrineInterpreter
        // for global (see also prune())
        //if ( classManager == null && (parent == null ) )
        // create our own class manager?

        setName(name);
        setParent(parent);
        setClassManager(classManager);

        // Register for notification of classloader change
        if (classManager != null)
            classManager.addListener(this);
    }

    // End constructors

    public void setName(String name) {
        this.nsName = name;
    }

    /**
     * The name of this namespace.  If the namespace is a method body
     * namespace then this is the name of the method.  If it's a class or
     * class instance then it's the name of the class.
     */
    public String getName() {
        return this.nsName;
    }

    /**
     * Set the node associated with the creation of this namespace.
     * This is used in debugging and to support the getInvocationLine()
     * and getInvocationText() methods.
     */
    void setNode(SimpleNode node) {
        callerInfoNode = node;
    }

    /**
     */
    SimpleNode getNode() {
        if (callerInfoNode != null)
            return callerInfoNode;
        if (parent != null)
            return parent.getNode();
        else
            return null;
    }

    /**
     * Resolve name to an object through this namespace.
     */
    public Object get(String name, KrineBasicInterpreter krineBasicInterpreter)
            throws UtilEvalException {
        CallStack callstack = new CallStack(this);
        return getNameResolver(name).toObject(callstack, krineBasicInterpreter);
    }

    /**
     * Set the variable through this namespace.
     * This method obeys the LOCAL_SCOPING property to determine how variables
     * are set.
     * <p>
     * Note: this method is primarily intended for use internally.  If you use
     * this method outside of the com.krine.lang package and wish to set variables with
     * primitive values you will have to wrap them using com.krine.lang.ast.Primitive.
     *
     * @param strictJava specifies whether strict java rules are applied.
     * @see Primitive
     * <p>
     * Setting a new variable (which didn't exist before) or removing
     * a variable causes a namespace change.
     */
    public void setVariable(String name, Object value, boolean strictJava)
            throws UtilEvalException {
        // if localscoping switch follow strictJava, else recurse
        boolean recurse = !KrineBasicInterpreter.LOCAL_SCOPING || strictJava;
        setVariable(name, value, strictJava, recurse);
    }

    /**
     * Set a variable explicitly in the local scope.
     */
    void setLocalVariable(
            String name, Object value, boolean strictJava)
            throws UtilEvalException {
        setVariable(name, value, strictJava, false/*recurse*/);
    }

    /**
     * Set the value of a the variable 'name' through this namespace.
     * The variable may be an existing or non-existing variable.
     * It may live in this namespace or in a parent namespace if recurse is
     * true.
     * <p>
     * Note: This method is not public and does *not* know about LOCAL_SCOPING.
     * Its caller methods must set recurse intelligently in all situations
     * (perhaps based on LOCAL_SCOPING).
     * <p>
     * <p>
     * Note: this method is primarily intended for use internally.  If you use
     * this method outside of the com.krine.lang package and wish to set variables with
     * primitive values you will have to wrap them using com.krine.lang.ast.Primitive.
     *
     * @param strictJava specifies whether strict java rules are applied.
     * @param recurse    determines whether we will search for the variable in
     *                   our parent's scope before assigning locally.
     * @see Primitive
     * <p>
     * Setting a new variable (which didn't exist before) or removing
     * a variable causes a namespace change.
     */
    void setVariable(
            String name, Object value, boolean strictJava, boolean recurse)
            throws UtilEvalException {
        ensureVariables();

        // primitives should have been wrapped
        if (value == null)
            throw new InterpreterException("null variable value");

        // Locate the variable definition if it exists.
        Variable existing = getVariableImpl(name, recurse);

        // Found an existing variable here (or above if recurse allowed)
        if (existing != null) {
            existing.setValue(value, Variable.ASSIGNMENT);
        } else {
            // No previous variable definition found here (or above if recurse)
            if (strictJava)
                throw new UtilEvalException(
                        "(Strict Java mode) Assignment to undeclared variable: "
                                + name);

            // If recurse, set global untyped var, else set it here.
            //NameSpace varScope = recurse ? getGlobal() : this;
            // This modification makes default allocation local
            NameSpace varScope = this;

            varScope.variables.put(
                    name, new Variable(name, value, null/*modifiers*/));

            // nameSpaceChanged() on new variable addition
            nameSpaceChanged();
        }
    }

    private void ensureVariables() {
        if (variables == null)
            variables = new HashMap<String, Variable>();
    }

    /**
     * Remove the variable from the namespace.
     */
    public void unsetVariable(String name) {
        if (variables != null) {
            variables.remove(name);
            nameSpaceChanged();
        }
    }

    /**
     * Get the names of variables defined in this namespace.
     * (This does not show variables in parent namespaces).
     */
    public String[] getVariableNames() {
        if (variables == null)
            return new String[0];
        else
            return variables.keySet().toArray(new String[0]);
    }

    /**
     * Get the names of methods declared in this namespace.
     * (This does not include methods in parent namespaces).
     */
    public String[] getMethodNames() {
        if (methods == null)
            return new String[0];
        else
            return methods.keySet().toArray(new String[0]);
    }

    /**
     * Get the methods defined in this namespace.
     * (This does not show methods in parent namespaces).
     * Note: This will probably be renamed getDeclaredMethods()
     */
    public KrineMethod[] getMethods() {
        if (methods == null) {
            return new KrineMethod[0];
        } else {
            List<KrineMethod> ret = new ArrayList<KrineMethod>();
            for (List<KrineMethod> list : methods.values()) {
                ret.addAll(list);
            }
            return ret.toArray(new KrineMethod[0]);
        }
    }

    /**
     * Get the parent namespace.
     * Note: this isn't quite the same as getSuper().
     * getSuper() returns 'this' if we are at the root namespace.
     */
    public NameSpace getParent() {
        return parent;
    }

    /**
     * Get the parent namespace' This reference or this namespace' This
     * reference if we are the top.
     */
    public This getSuper(KrineBasicInterpreter declaringKrineBasicInterpreter) {
        if (parent != null)
            return parent.getThis(declaringKrineBasicInterpreter);
        else
            return getThis(declaringKrineBasicInterpreter);
    }

    /**
     * Get the top level namespace or this namespace if we are the top.
     * Note: this method should probably return type com.krine.lang.ast.This to be consistent
     * with getThis();
     */
    public This getGlobal(KrineBasicInterpreter declaringKrineBasicInterpreter) {
        if (parent != null)
            return parent.getGlobal(declaringKrineBasicInterpreter);
        else
            return getThis(declaringKrineBasicInterpreter);
    }


    /**
     * A This object is a thin layer over a namespace, comprising a com.krine.lang object
     * context.  It handles things like the interface types the com.krine.lang object
     * supports and aspects of method invocation on it.
     * <p>
     * <p>
     * The declaringKrineBasicInterpreter is here to support callbacks from Java through
     * generated proxies.  The scripted object "remembers" who created it for
     * things like printing messages and other per-krineBasicInterpreter phenomenon
     * when called externally from Java.
     */
	/*
		Note: we need a singleton here so that things like 'this == this' work
		(and probably a good idea for speed).

		Caching a single instance here seems technically incorrect,
		considering the declaringKrineBasicInterpreter could be different under some
		circumstances.  (Case: a child krineBasicInterpreter running a source() / eval()
		command ).  However the effect is just that the main krineBasicInterpreter that
		executes your script should be the one involved in call-backs from Java.

		I do not know if there are corner cases where a child krineBasicInterpreter would
		be the first to use a This reference in a namespace or if that would
		even cause any problems if it did...  We could do some experiments
		to find out... and if necessary we could cache on a per krineBasicInterpreter
		basis if we had weak references...  We might also look at skipping 
		over child interpreters and going to the parent for the declaring 
		krineBasicInterpreter, so we'd be sure to get the top krineBasicInterpreter.
	*/
    public This getThis(KrineBasicInterpreter declaringKrineBasicInterpreter) {
        if (thisReference == null)
            thisReference = This.getThis(this, declaringKrineBasicInterpreter);

        return thisReference;
    }

    public KrineClassManager getClassManager() {
        if (classManager != null)
            return classManager;
        if (parent != null && parent != JAVACODE)
            return parent.getClassManager();

        classManager = KrineClassManager.createClassManager(null/*interp*/);

        //KrineInterpreter.debug("No class manager namespace:" +this);
        return classManager;
    }

    void setClassManager(KrineClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Used for serialization
     */
    public void prune() {
        // Cut off from parent, we must have our own class manager.
        // Can't do this in the run() command (needs to resolve stuff)
        // Should we do it by default when we create a namespace will no
        // parent of class manager?

        if (this.classManager == null)
// XXX if we keep the createClassManager in getClassManager then we can axe
// this?
            setClassManager(
                    KrineClassManager.createClassManager(null/*interp*/));

        setParent(null);
    }

    public void setParent(NameSpace parent) {
        this.parent = parent;

        // If we are disconnected from root we need to handle the def imports
        if (parent == null)
            loadDefaultImports();
    }

    /**
     * Get the specified variable in this namespace or a parent namespace.
     * <p>
     * Note: this method is primarily intended for use internally.  If you use
     * this method outside of the com.krine.lang package you will have to use
     * Primitive.unwrap() to get primitive values.
     *
     * @return The variable value or Primitive.VOID if it is not defined.
     * @see Primitive#unwrap(Object)
     */
    public Object getVariable(String name)
            throws UtilEvalException {
        return getVariable(name, true);
    }

    /**
     * Get the specified variable in this namespace.
     *
     * @param recurse If recurse is true then we recursively search through
     *                parent namespaces for the variable.
     *                <p>
     *                Note: this method is primarily intended for use internally.  If you use
     *                this method outside of the com.krine.lang package you will have to use
     *                Primitive.unwrap() to get primitive values.
     * @return The variable value or Primitive.VOID if it is not defined.
     * @see Primitive#unwrap(Object)
     */
    public Object getVariable(String name, boolean recurse)
            throws UtilEvalException {
        Variable var = getVariableImpl(name, recurse);
        return unwrapVariable(var);
    }

    /**
     * Locate a variable and return the Variable object with optional
     * recursion through parent name spaces.
     * <p/>
     * If this namespace is static, return only static variables.
     *
     * @return the Variable value or null if it is not defined
     */
    protected Variable getVariableImpl(String name, boolean recurse)
            throws UtilEvalException {
        Variable var = null;

        // Change import precedence if we are a class body/instance
        // Get imported first.
        if (var == null && isClass)
            var = getImportedVar(name);

        if (var == null && variables != null)
            var = variables.get(name);

        // Change import precedence if we are a class body/instance
        if (var == null && !isClass)
            var = getImportedVar(name);

        // try parent
        if (recurse && (var == null) && (parent != null))
            var = parent.getVariableImpl(name, recurse);

        return var;
    }

    /*
        Get variables declared in this namespace.
    */
    public Variable[] getDeclaredVariables() {
        if (variables == null)
            return new Variable[0];
        return variables.values().toArray(new Variable[0]);
    }

    /**
     * Unwrap a variable to its value.
     *
     * @return return the variable value.  A null var is mapped to
     * Primitive.VOID
     */
    protected Object unwrapVariable(Variable var)
            throws UtilEvalException {
        return (var == null) ? Primitive.VOID : var.getValue();
    }

    /**
     * @deprecated See #setTypedVariable( String, Class, Object, Modifiers )
     */
    public void setTypedVariable(
            String name, Class type, Object value, boolean isFinal)
            throws UtilEvalException {
        Modifiers modifiers = new Modifiers();
        if (isFinal)
            modifiers.addModifier(Modifiers.FIELD, "final");
        setTypedVariable(name, type, value, modifiers);
    }

    /**
     * Declare a variable in the local scope and set its initial value.
     * Value may be null to indicate that we would like the default value
     * for the variable type. (e.g.  0 for integer types, null for object
     * types).  An existing typed variable may only be set to the same type.
     * If an untyped variable of the same name exists it will be overridden
     * with the new typed var.
     * The set will perform a Types.getAssignableForm() on the value if
     * necessary.
     * <p>
     * <p>
     * Note: this method is primarily intended for use internally.  If you use
     * this method outside of the com.krine.lang package and wish to set variables with
     * primitive values you will have to wrap them using com.krine.lang.ast.Primitive.
     *
     * @param value     If value is null, you'll get the default value for the type
     * @param modifiers may be null
     * @see Primitive
     */
    public void setTypedVariable(
            String name, Class type, Object value, Modifiers modifiers)
            throws UtilEvalException {
        //checkVariableModifiers( name, modifiers );

        ensureVariables();

        // Setting a typed variable is always a local operation.
        Variable existing = getVariableImpl(name, false/*recurse*/);


        // Null value is just a declaration
        // Note: we might want to keep any existing value here instead of reset
	/*
	// Moved to Variable
		if ( value == null )
			value = Primitive.getDefaultValue( type );
	*/

        // does the variable already exist?
        if (existing != null) {
            // Is it typed?
            if (existing.getType() != null) {
                // If it had a different type throw error.
                // This allows declaring the same var again, but not with
                // a different (even if assignable) type.
                if (existing.getType() != type) {
                    throw new UtilEvalException("Typed variable: " + name
                            + " was previously declared with type: "
                            + existing.getType());
                } else {
                    // else set it and return
                    existing.setValue(value, Variable.DECLARATION);
                    return;
                }
            }
            // Careful here:
            // else fall through to override and install the new typed version
        }

        // Add the new typed var
        variables.put(name, new Variable(name, type, value, modifiers));
    }

    /**
     Dissallow static vars outside of a class
     @param name is here just to allow the error message to use it
     protected void checkVariableModifiers( String name, Modifiers modifiers )
     throws UtilEvalException
     {
     if ( modifiers!=null && modifiers.hasModifier("static") )
     throw new UtilEvalException(
     "Can't declare static variable outside of class: "+name );
     }
     */

    /**
     * Note: this is primarily for internal use.
     *
     * @see KrineBasicInterpreter#source(String)
     * @see KrineBasicInterpreter#eval(String)
     */
    public void setMethod(KrineMethod method)
            throws UtilEvalException {
        //checkMethodModifiers( method );

        if (methods == null)
            methods = new HashMap<String, List<KrineMethod>>();

        String name = method.getName();
        List<KrineMethod> list = methods.get(name);

        if (list == null) {
            methods.put(name, Collections.singletonList(method));
        } else {
            if (!(list instanceof ArrayList)) {
                list = new ArrayList<KrineMethod>(list);
                methods.put(name, list);
            }
            list.remove(method);
            list.add(method);
        }
    }

    /**
     * @see #getMethod(String, Class [], boolean)
     * @see #getMethod(String, Class [])
     */
    public KrineMethod getMethod(String name, Class[] sig)
            throws UtilEvalException {
        return getMethod(name, sig, false/*declaredOnly*/);
    }

    /**
     * Get the com.krine.lang method matching the specified signature declared in
     * this name space or a parent.
     * <p>
     * Note: this method is primarily intended for use internally.  If you use
     * this method outside of the com.krine.lang package you will have to be familiar
     * with Krine's use of the Primitive wrapper class.
     *
     * @param declaredOnly if true then only methods declared directly in this
     *                     namespace will be found and no inherited or imported methods will
     *                     be visible.
     * @return the KrineMethod or null if not found
     * @see Primitive
     */
    public KrineMethod getMethod(
            String name, Class[] sig, boolean declaredOnly)
            throws UtilEvalException {
        KrineMethod method = null;

        // Change import precedence if we are a class body/instance
        // Get import first.
        if (method == null && isClass && !declaredOnly)
            method = getImportedMethod(name, sig);

        if (method == null && methods != null) {
            List<KrineMethod> list = methods.get(name);

            if (list != null) {
                // Apply most specific signature matching
                Class[][] candidates = new Class[list.size()][];
                for (int i = 0; i < candidates.length; i++)
                    candidates[i] = list.get(i).getParameterTypes();

                int match =
                        Reflect.findMostSpecificSignature(sig, candidates);
                if (match != -1)
                    method = list.get(match);
            }
        }

        if (method == null && !isClass && !declaredOnly)
            method = getImportedMethod(name, sig);

        // try parent
        if (!declaredOnly && (method == null) && (parent != null))
            return parent.getMethod(name, sig);

        return method;
    }

    /**
     * Import a class name.
     * Subsequent imports override earlier ones
     */
    public void importClass(String name) {
        if (importedClasses == null)
            importedClasses = new HashMap<String, String>();

        importedClasses.put(Name.suffix(name, 1), name);
        nameSpaceChanged();
    }

    /**
     * subsequent imports override earlier ones
     */
    public void importPackage(String name) {
        if (importedPackages == null)
            importedPackages = new ArrayList<String>();

        // If it exists, remove it and add it at the end (avoid memory leak)
        importedPackages.remove(name);

        importedPackages.add(name);
        nameSpaceChanged();
    }

    /**
     * Import scripted or compiled Krine commands in the following package
     * in the classpath.  You may use either "/" path or "." package notation.
     * e.g. importCommands("/com.krine.lang/commands") or importCommands("com.krine.lang.commands")
     * are equivalent.  If a relative path style specifier is used then it is
     * made into an absolute path by prepending "/".
     */
    public void importCommands(String name) {
        if (importedCommands == null)
            importedCommands = new ArrayList<String>();

        // dots to slashes
        name = name.replace('.', '/');
        // absolute
        if (!name.startsWith("/"))
            name = "/" + name;
        // remove trailing (but preserve case of simple "/")
        if (name.length() > 1 && name.endsWith("/"))
            name = name.substring(0, name.length() - 1);

        // If it exists, remove it and add it at the end (avoid memory leak)
        importedCommands.remove(name);

        importedCommands.add(name);
        nameSpaceChanged();
    }

    /**
     * A command is a scripted method or compiled command class implementing a
     * specified method signature.  Commands are loaded from the classpath
     * and may be imported using the importCommands() method.
     * <p/>
     * <p>
     * This method searches the imported commands packages for a script or
     * command object corresponding to the name of the method.  If it is a
     * script the script is sourced into this namespace and the KrineMethod for
     * the requested signature is returned.  If it is a compiled class the
     * class is returned.  (Compiled command classes implement static invoke()
     * methods).
     * <p/>
     * <p>
     * The imported packages are searched in reverse order, so that later
     * imports take priority.
     * Currently only the first object (script or class) with the appropriate
     * name is checked.  If another, overloaded form, is located in another
     * package it will not currently be found.  This could be fixed.
     * <p/>
     *
     * @param name     is the name of the desired command method
     * @param argTypes is the signature of the desired command method.
     * @return a KrineMethod, Class, or null if no such command is found.
     * @throws UtilEvalException if loadScriptedCommand throws UtilEvalException
     *                       i.e. on errors loading a script that was found
     */
    public Object getCommand(
            String name, Class[] argTypes, KrineBasicInterpreter krineBasicInterpreter)
            throws UtilEvalException {
        if (KrineBasicInterpreter.DEBUG) KrineBasicInterpreter.debug("getCommand: " + name);
        KrineClassManager dcm = krineBasicInterpreter.getClassManager();

        if (importedCommands != null) {
            // loop backwards for precedence
            for (int i = importedCommands.size() - 1; i >= 0; i--) {
                String path = importedCommands.get(i);

                String scriptPath;
                if (path.equals("/"))
                    scriptPath = path + name + ".com.krine.lang";
                else
                    scriptPath = path + "/" + name + ".com.krine.lang";

                KrineBasicInterpreter.debug("searching for script: " + scriptPath);

                InputStream in = dcm.getResourceAsStream(scriptPath);

                if (in != null)
                    return loadScriptedCommand(
                            in, name, argTypes, scriptPath, krineBasicInterpreter);

                // Chop leading "/" and change "/" to "."
                String className;
                if (path.equals("/"))
                    className = name;
                else
                    className = path.substring(1).replace('/', '.') + "." + name;

                KrineBasicInterpreter.debug("searching for class: " + className);
                Class clazz = dcm.classForName(className);
                if (clazz != null)
                    return clazz;
            }
        }

        if (parent != null)
            return parent.getCommand(name, argTypes, krineBasicInterpreter);
        else
            return null;
    }

    protected KrineMethod getImportedMethod(String name, Class[] sig)
            throws UtilEvalException {
        // Try object imports
        if (importedObjects != null)
            for (int i = 0; i < importedObjects.size(); i++) {
                Object object = importedObjects.get(i);
                Class clas = object.getClass();
                Method method = Reflect.resolveJavaMethod(
                        getClassManager(), clas, name, sig, false/*onlyStatic*/);
                if (method != null)
                    return new KrineMethod(method, object);
            }

        // Try static imports
        if (importedStatic != null)
            for (int i = 0; i < importedStatic.size(); i++) {
                Class clas = importedStatic.get(i);
                Method method = Reflect.resolveJavaMethod(
                        getClassManager(), clas, name, sig, true/*onlyStatic*/);
                if (method != null)
                    return new KrineMethod(method, null/*object*/);
            }

        return null;
    }

    protected Variable getImportedVar(String name)
            throws UtilEvalException {
        // Try object imports
        if (importedObjects != null)
            for (int i = 0; i < importedObjects.size(); i++) {
                Object object = importedObjects.get(i);
                Class clas = object.getClass();
                Field field = Reflect.resolveJavaField(
                        clas, name, false/*onlyStatic*/);
                if (field != null)
                    return new Variable(
                            name, field.getType(), new LeftValue(object, field));
            }

        // Try static imports
        if (importedStatic != null)
            for (int i = 0; i < importedStatic.size(); i++) {
                Class clas = importedStatic.get(i);
                Field field = Reflect.resolveJavaField(
                        clas, name, true/*onlyStatic*/);
                if (field != null)
                    return new Variable(name, field.getType(), new LeftValue(field));
            }

        return null;
    }

    /**
     * Load a command script from the input stream and find the KrineMethod in
     * the target namespace.
     *
     * @throws UtilEvalException on error in parsing the script or if the the
     *                       method is not found after parsing the script.
     */
	/*
		If we want to support multiple commands in the command path we need to
		change this to not throw the exception.
	*/
    private KrineMethod loadScriptedCommand(
            InputStream in, String name, Class[] argTypes, String resourcePath,
            KrineBasicInterpreter krineBasicInterpreter)
            throws UtilEvalException {
        try {
            krineBasicInterpreter.eval(
                    new InputStreamReader(in), this, resourcePath);
        } catch (EvalError e) {
		/* 
			Here we catch any EvalError from the krineBasicInterpreter because we are
			using it as a tool to load the command, not as part of the
			execution path.
		*/
            KrineBasicInterpreter.debug(e.toString());
            throw new UtilEvalException(
                    "Error loading script: " + e.getMessage(), e);
        }

        // Look for the loaded command
        KrineMethod meth = getMethod(name, argTypes);
		/*
		if ( meth == null )
			throw new UtilEvalException("Loaded resource: " + resourcePath +
				"had an error or did not contain the correct method" );
		*/

        return meth;
    }

    /**
     * Helper that caches class.
     */
    void cacheClass(String name, Class c) {
        if (classCache == null) {
            classCache = new HashMap<String, Class>();
            //cacheCount++; // debug
        }

        classCache.put(name, c);
    }

    /**
     * Load a class through this namespace taking into account imports.
     * The class search will proceed through the parent namespaces if
     * necessary.
     *
     * @return null if not found.
     */
    public Class getClass(String name)
            throws UtilEvalException {
        Class c = getClassImpl(name);
        if (c != null)
            return c;
        else
            // implement the recursion for getClassImpl()
            if (parent != null)
                return parent.getClass(name);
            else
                return null;
    }

    /**
     * Implementation of getClass()
     * <p>
     * Load a class through this namespace taking into account imports.
     * <p>
     * <p>
     * Check the cache first.  If an unqualified name look for imported
     * class or package.  Else try to load absolute name.
     * <p>
     * <p>
     * This method implements caching of unqualified names (normally imports).
     * Qualified names are cached by the KrineClassManager.
     * Unqualified absolute class names (e.g. unpackaged Foo) are cached too
     * so that we don't go searching through the imports for them each time.
     *
     * @return null if not found.
     */
    private Class getClassImpl(String name)
            throws UtilEvalException {
        Class c = null;

        // Check the cache
        if (classCache != null) {
            c = classCache.get(name);

            if (c != null)
                return c;
        }

        // Unqualified (simple, non-compound) name
        boolean unqualifiedName = !Name.isCompound(name);

        // Unqualified name check imported
        if (unqualifiedName) {
            // Try imported class
            if (c == null)
                c = getImportedClassImpl(name);

            // if found as imported also cache it
            if (c != null) {
                cacheClass(name, c);
                return c;
            }
        }

        // Try absolute
        c = classForName(name);
        if (c != null) {
            // Cache unqualified names to prevent import check again
            if (unqualifiedName)
                cacheClass(name, c);
            return c;
        }

        // Not found
        if (KrineBasicInterpreter.DEBUG)
            KrineBasicInterpreter.debug("getClass(): " + name + " not	found in " + this);
        return null;
    }

    /**
     * Try to make the name into an imported class.
     * This method takes into account only imports (class or package)
     * found directly in this NameSpace (no parent chain).
     */
    private Class getImportedClassImpl(String name)
            throws UtilEvalException {
        // Try explicitly imported class, e.g. import foo.Bar;
        String fullname = null;
        if (importedClasses != null)
            fullname = importedClasses.get(name);

        // not sure if we should really recurse here for explicitly imported
        // class in parent...

        if (fullname != null) {
			/*
				Found the full name in imported classes.
			*/
            // Try to make the full imported name
            Class clas = classForName(fullname);

            if (clas != null)
                return clas;

            // Handle imported inner class case
            // Imported full name wasn't found as an absolute class
            // If it is compound, try to resolve to an inner class.
            // (maybe this should happen in the KrineClassManager?)

            if (Name.isCompound(fullname))
                try {
                    clas = getNameResolver(fullname).toClass();
                } catch (ClassNotFoundException e) { /* not a class */ }
            else if (KrineBasicInterpreter.DEBUG) KrineBasicInterpreter.debug(
                    "imported unpackaged name not found:" + fullname);

            // If found cache the full name in the KrineClassManager
            if (clas != null) {
                // (should we cache info in not a class case too?)
                getClassManager().cacheClassInfo(fullname, clas);
                return clas;
            }

            // It was explicitly imported, but we don't know what it is.
            // should we throw an error here??
            return null;
        }

		/*
			Try imported packages, e.g. "import foo.bar.*;"
			in reverse order of import...
			(give later imports precedence...)
		*/
        if (importedPackages != null)
            for (int i = importedPackages.size() - 1; i >= 0; i--) {
                String s = importedPackages.get(i) + "." + name;
                Class c = classForName(s);
                if (c != null)
                    return c;
            }

        KrineClassManager dcm = getClassManager();
		/*
			Try super import if available
			Note: we do this last to allow explicitly imported classes
			and packages to take priority.  This method will also throw an
			error indicating ambiguity if it exists...
		*/
        if (dcm.hasSuperImport()) {
            String s = dcm.getClassNameByUnqName(name);
            if (s != null)
                return classForName(s);
        }

        return null;
    }

    private Class classForName(String name) {
        return getClassManager().classForName(name);
    }

    /**
     * Implements NameSource
     *
     * @return all variable and method names in this and all parent
     * namespaces
     */
    public String[] getAllNames() {
        List<String> list = new ArrayList<String>();
        getAllNamesAux(list);
        return list.toArray(new String[0]);
    }

    /**
     * Helper for implementing NameSource
     */
    protected void getAllNamesAux(List<String> list) {
        list.addAll(variables.keySet());
        list.addAll(methods.keySet());
        if (parent != null)
            parent.getAllNamesAux(list);
    }

    List<Listener> nameSourceListeners;

    /**
     * Implements NameSource
     * Add a listener who is notified upon changes to names in this space.
     */
    public void addNameSourceListener(NameSource.Listener listener) {
        if (nameSourceListeners == null)
            nameSourceListeners = new ArrayList<Listener>();
        nameSourceListeners.add(listener);
    }

    /**
     * Perform "import *;" causing the entire classpath to be mapped.
     * This can take a while.
     */
    public void doSuperImport()
            throws UtilEvalException {
        getClassManager().doSuperImport();
    }


    public String toString() {
        return (nsName == null
                ? super.toString()
                : nsName)
                + (isClass ? " (isClass) " : "")
                + (isMethod ? " (method) " : "")
                + (classStatic != null ? " (class static) " : "")
                + (classInstance != null ? " (class instance) " : "");
    }

    /*
        For serialization.
        Don't serialize non-serializable objects.
    */
    private synchronized void writeObject(java.io.ObjectOutputStream s)
            throws IOException {
        // clear name resolvers... don't know if this is necessary.
        names = null;

        s.defaultWriteObject();
    }

    /**
     * Invoke a method in this namespace with the specified args and
     * krineBasicInterpreter reference.  No caller information or call stack is
     * required.  The method will appear as if called externally from Java.
     * <p>
     *
     * @see This.invokeMethod(
     * String methodName, Object [] args, KrineBasicInterpreter interpreter,
     * CallStack callstack, SimpleNode callerInfo, boolean )
     */
    public Object invokeMethod(
            String methodName, Object[] args, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        return invokeMethod(
                methodName, args, krineBasicInterpreter, null, null);
    }

    /**
     * This method simply delegates to This.invokeMethod();
     * <p>
     *
     * @see This.invokeMethod(
     * String methodName, Object [] args, KrineBasicInterpreter interpreter,
     * CallStack callstack, SimpleNode callerInfo )
     */
    public Object invokeMethod(
            String methodName, Object[] args, KrineBasicInterpreter krineBasicInterpreter,
            CallStack callstack, SimpleNode callerInfo)
            throws EvalError {
        return getThis(krineBasicInterpreter).invokeMethod(
                methodName, args, krineBasicInterpreter, callstack, callerInfo,
                false/*declaredOnly*/);
    }

    /**
     * Clear all cached classes and names
     */
    public void classLoaderChanged() {
        nameSpaceChanged();
    }

    /**
     * Clear all cached classes and names
     */
    public void nameSpaceChanged() {
        classCache = null;
        names = null;
    }

    /**
     * Import standard packages.  Currently:
     * <pre>
     * importClass("com.krine.lang.ast.EvalError");
     * importClass("com.krine.lang.KrineInterpreter");
     * importPackage("javax.swing.event");
     * importPackage("javax.swing");
     * importPackage("java.awt.event");
     * importPackage("java.awt");
     * importPackage("java.net");
     * importPackage("java.util");
     * importPackage("java.io");
     * importPackage("java.lang");
     * importCommands("/com.krine.lang/commands");
     * </pre>
     */
    public void loadDefaultImports() {
        /**
         Note: the resolver looks through these in reverse order, per
         precedence rules...  so for max efficiency put the most common
         ones later.
         */
        importClass("com.krine.lang.ast.EvalError");
        importClass("com.krine.lang.KrineInterpreter");
        importClass("com.krine.interpreter.KrineInterpreter");
        importPackage("java.net");
        importPackage("java.util");
        importPackage("java.io");
        importPackage("java.lang");
    }

    /**
     * This is the factory for Name objects which resolve names within
     * this namespace (e.g. toObject(), toClass(), toLeftValue()).
     * <p>
     * <p>
     * This was intended to support name resolver caching, allowing
     * Name objects to cache info about the resolution of names for
     * performance reasons.  However this not proven useful yet.
     * <p>
     * <p>
     * We'll leave the caching as it will at least minimize Name object
     * creation.
     * <p>
     * <p>
     * (This method would be called getName() if it weren't already used for
     * the simple name of the NameSpace)
     * <p>
     * <p>
     * This method was public for a time, which was a mistake.
     * Use get() instead.
     */
    public Name getNameResolver(String ambigname) {
        if (names == null)
            names = new HashMap<>();

        Name name = names.get(ambigname);

        if (name == null) {
            name = new Name(this, ambigname);
            names.put(ambigname, name);
        }

        return name;
    }

    public int getInvocationLine() {
        SimpleNode node = getNode();
        if (node != null)
            return node.getLineNumber();
        else
            return -1;
    }

    public String getInvocationText() {
        SimpleNode node = getNode();
        if (node != null)
            return node.getText();
        else
            return "<invoked from Java code>";
    }

    /**
     * This is a helper method for working inside of com.krine.lang scripts and commands.
     * In that context it is impossible to see a ClassIdentifier object
     * for what it is.  Attempting to access a method on a ClassIdentifier
     * will look like a static method invocation.
     * <p>
     * This method is in NameSpace for convenience (you don't have to import
     * com.krine.lang.classpath.ClassIdentifier to use it );
     */
    public static Class identifierToClass(ClassIdentifier ci) {
        return ci.getTargetClass();
    }


    /**
     * Clear all variables, methods, and imports from this namespace.
     * If this namespace is the root, it will be reset to the default
     * imports.
     *
     * @see #loadDefaultImports()
     */
    public void clear() {
        variables = null;
        methods = null;
        importedClasses = null;
        importedPackages = null;
        importedCommands = null;
        importedObjects = null;
        if (parent == null)
            loadDefaultImports();
        classCache = null;
        names = null;
    }

    /**
     * Import a compiled Java object's methods and variables into this
     * namespace.  When no scripted method / command or variable is found
     * locally in this namespace method / fields of the object will be
     * checked.  Objects are checked in the order of import with later imports
     * taking precedence.
     * <p/>
     */
	/*
		Note: this impor pattern is becoming common... could factor it out into
		an importedObject Vector class.
	*/
    public void importObject(Object obj) {
        if (importedObjects == null)
            importedObjects = new ArrayList<Object>();

        // If it exists, remove it and add it at the end (avoid memory leak)
        importedObjects.remove(obj);

        importedObjects.add(obj);
        nameSpaceChanged();

    }

    /**
     */
    public void importStatic(Class clas) {
        if (importedStatic == null)
            importedStatic = new ArrayList<Class>();

        // If it exists, remove it and add it at the end (avoid memory leak)
        importedStatic.remove(clas);

        importedStatic.add(clas);
        nameSpaceChanged();
    }

    /**
     * Set the package name for classes defined in this namespace.
     * Subsequent sets override the package.
     */
    void setPackage(String packageName) {
        this.packageName = packageName;
    }

    String getPackage() {
        if (packageName != null)
            return packageName;

        if (parent != null)
            return parent.getPackage();

        return null;
    }


    NameSpace copy() {
        try {
            final NameSpace clone = (NameSpace) clone();
            clone.thisReference = null;
            clone.variables = clone(variables);
            clone.methods = clone(methods);
            clone.importedClasses = clone(importedClasses);
            clone.importedPackages = clone(importedPackages);
            clone.importedCommands = clone(importedCommands);
            clone.importedObjects = clone(importedObjects);
            clone.importedStatic = clone(importedStatic);
            clone.names = clone(names);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }


    private <K, V> Map<K, V> clone(final Map<K, V> map) {
        if (map == null) {
            return null;
        }
        return new HashMap<K, V>(map);
    }


    private <T> List<T> clone(final List<T> list) {
        if (list == null) {
            return null;
        }
        return new ArrayList<T>(list);
    }

}