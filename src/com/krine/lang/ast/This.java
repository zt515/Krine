package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.StringUtil;
import krine.core.KRuntimeException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;


/**
 * 'This' is the type of krine scripted objects.
 * A 'This' object is a krine scripted object context.  It holds a nameSpace
 * reference and implements event listeners and various other interfaces.
 * <p>
 * This holds a reference to the declaring krineBasicInterpreter for callbacks from
 * outside of krine.
 */
public final class This implements java.io.Serializable, Runnable {
    /**
     * The nameSpace that this This reference wraps.
     */
    public final NameSpace namespace;
    private final InvocationHandler invocationHandler = new Handler();
    /**
     * This is the krineBasicInterpreter running when the This ref was created.
     * It's used as a default krineBasicInterpreter for callback through the This
     * where there is no current krineBasicInterpreter instance
     * e.g. interface proxy or event call backs from outside of krine.
     */
    transient KrineBasicInterpreter declaringKrineBasicInterpreter;
    /**
     * A cache of proxy interface handlers.
     * Currently just one per interface.
     */
    private Map<Integer, Object> interfaces;

    This(NameSpace namespace, KrineBasicInterpreter declaringKrineBasicInterpreter) {
        this.namespace = namespace;
        this.declaringKrineBasicInterpreter = declaringKrineBasicInterpreter;
        //initCallStack( nameSpace );
    }

    /**
     Get a version of this scripted object implementing the specified
     interface.
     */

    /**
     * getThis() is a factory for krine.This type references.  The capabilities
     * of ".this" references in krine are version dependent up until jdk1.3.
     * The version dependence was to support different default interface
     * implementations.  i.e. different sets of listener interfaces which
     * scripted objects were capable of implementing.  In jdk1.3 the
     * reflection proxy mechanism was introduced which allowed us to
     * implement arbitrary interfaces.  This is fantastic.
     * <p>
     * A This object is a thin layer over a nameSpace, comprising a krine object
     * context.  We create it here only if needed for the nameSpace.
     * <p>
     * Note: this method could be considered slow because of the way it
     * dynamically factories objects.  However I've also done tests where
     * I hard-code the factory to return JThis and see no change in the
     * rough test suite time.  This references are also cached in NameSpace.
     */
    public static This getThis(
            NameSpace namespace, KrineBasicInterpreter declaringKrineBasicInterpreter) {
        return new This(namespace, declaringKrineBasicInterpreter);
    }

    /**
     * Bind a This reference to a parent's nameSpace with the specified
     * declaring krineBasicInterpreter.  Also re-init the callStack.  It's necessary
     * to bind a This reference before it can be used after deserialization.
     * This is used by the krine load() command.
     * <p>
     * <p>
     * This is a static utility method because it's used by a krine command
     * bind() and the krineBasicInterpreter doesn't currently allow access to direct
     * methods of This objects (small hack)
     */
    public static void bind(
            This ths, NameSpace namespace, KrineBasicInterpreter declaringKrineBasicInterpreter) {
        ths.namespace.setParent(namespace);
        ths.declaringKrineBasicInterpreter = declaringKrineBasicInterpreter;
    }

    /**
     * Allow invocations of these method names on This type objects.
     * Don't give krine.This a chance to override their behavior.
     * <p>
     * <p>
     * If the method is passed here the invocation will actually happen on
     * the krine.This object via the regular reflective method invocation
     * mechanism.  If not, then the method is evaluated by krine.This itself
     * as a scripted method call.
     */
    public static boolean isExposedThisMethod(String name) {
        return
                name.equals("getClass")
                        || name.equals("invokeMethod")
                        || name.equals("asInterface")
                        // These are necessary to let us test synchronization from scripts
                        || name.equals("wait")
                        || name.equals("notify")
                        || name.equals("notifyAll");
    }

    /**
     * Get the interpreter from This class.
     *
     * @param aThis This class object.
     * @return KrineBasicInterpreter the This object has.
     */
    public static KrineBasicInterpreter getInterpreter(This aThis) throws KRuntimeException {
        return aThis.declaringKrineBasicInterpreter;
    }

    /**
     * Get dynamic proxy for interface, caching those it creates.
     */
    public Object getInterface(Class clazz) {
        return getInterface(new Class[]{clazz});
    }

    /**
     * Get dynamic proxy for interface, caching those it creates.
     */
    public Object getInterface(Class[] ca) {
        if (interfaces == null)
            interfaces = new HashMap<>();

        // Make a hash of the interface hashcode in order to cache them
        int hash = 21;
        for (Class aCa : ca) hash *= aCa.hashCode() + 3;
        Integer hashKey = hash;

        Object interf = interfaces.get(hashKey);

        if (interf == null) {
            ClassLoader classLoader = ca[0].getClassLoader(); // ?
            interf = Proxy.newProxyInstance(
                    classLoader, ca, invocationHandler);
            interfaces.put(hashKey, interf);
        }

        return interf;
    }

    public NameSpace getNameSpace() {
        return namespace;
    }

    public String toString() {
        return namespace.toString();
    }

    public void run() {
        try {
            invokeMethod("run", new Object[0]);
        } catch (EvalError e) {
            declaringKrineBasicInterpreter.error(
                    "Exception in runnable:" + e);
        }
    }

    /**
     * Invoke specified method as from outside java code, using the
     * declaring krineBasicInterpreter and current nameSpace.
     * The call stack will indicate that the method is being invoked from
     * outside of krine in native java code.
     * Note: you must still wrap/unwrap args/return values using
     * Primitive/Primitive.unwrap() for use outside of Krine.
     *
     * @see Primitive
     */
    public Object invokeMethod(String name, Object[] args)
            throws EvalError {
        // null callStack, one will be created for us
        return invokeMethod(
                name, args, null/*declaringKrineBasicInterpreter*/, null, null,
                false/*declaredOnly*/);
    }

    /**
     * Invoke a method in this nameSpace with the specified args,
     * krineBasicInterpreter reference, callStack, and caller info.
     * <p>
     * <p>
     * Note: If you use this method outside of the krine package and wish to
     * use variables with primitive values you will have to wrap them using
     * krine.Primitive.  Consider using This asInterface() to make a true Java
     * interface for invoking your scripted methods.
     * <p>
     * <p>
     * This method also implements the default object protocol of toString(),
     * hashCode() and equals() and the invoke() meta-method handling as a
     * last resort.
     * <p>
     * <p>
     * Note: The invoke() meta-method will not catch the Object protocol
     * methods (toString(), hashCode()...).  If you want to override them you
     * have to script them directly.
     * <p>
     *
     * @param callStack    if callStack is null a new CallStack will be created and
     *                     initialized with this nameSpace.
     * @param declaredOnly if true then only methods declared directly in the
     *                     nameSpace will be visible - no inherited or imported methods will
     *                     be visible.
     * @see Primitive
     */
    /*
        invokeMethod() here is generally used by outside code to callback
		into the krine krineBasicInterpreter. e.g. when we are acting as an interface
		for a scripted listener, etc.  In this case there is no real call stack
		so we make a default one starting with the special JAVA_CODE nameSpace
		and our nameSpace as the next.
	*/
    public Object invokeMethod(
            String methodName, Object[] args,
            KrineBasicInterpreter krineBasicInterpreter, CallStack callStack, SimpleNode callerInfo,
            boolean declaredOnly)
            throws EvalError {
		/*
			Wrap nulls.
			This is a bit of a cludge to address a deficiency in the class
			generator whereby it does not wrap nulls on method delegate.  See
			Class Generator.java.  If we fix that then we can remove this.
			(just have to generate the code there.)
		*/
        if (args == null) {
            args = new Object[0];
        } else {
            Object[] oa = new Object[args.length];
            for (int i = 0; i < args.length; i++)
                oa[i] = (args[i] == null ? Primitive.NULL : args[i]);
            args = oa;
        }

        if (krineBasicInterpreter == null)
            krineBasicInterpreter = declaringKrineBasicInterpreter;
        if (callStack == null)
            callStack = new CallStack(namespace);
        if (callerInfo == null)
            callerInfo = SimpleNode.JAVA_CODE;

        // Find the krine method
        Class[] types = Types.getTypes(args);
        KrineMethod krineMethod = null;
        try {
            krineMethod = namespace.getMethod(methodName, types, declaredOnly);
        } catch (UtilEvalException e) {
            // leave null
        }

        if (krineMethod != null)
            return krineMethod.invoke(args, krineBasicInterpreter, callStack, callerInfo);

		/*
			No scripted method of that name.
			Implement the required part of the Object protocol:
				public int hashCode();
				public boolean equals(java.lang.Object);
				public java.lang.String toString();
			if these were not handled by scripted methods we must provide
			a default impl.
		*/
        // a default toString() that shows the interfaces we implement
        if (methodName.equals("toString") && args.length == 0)
            return toString();

        // a default hashCode()
        if (methodName.equals("hashCode") && args.length == 0)
            return this.hashCode();

        // a default equals() testing for equality with the This reference
        if (methodName.equals("equals") && args.length == 1) {
            Object obj = args[0];
            return this == obj;
        }

        // a default clone()
        if (methodName.equals("clone") && args.length == 0) {
            NameSpace ns = new NameSpace(namespace, namespace.getName() + " clone");
            try {
                for (String varName : namespace.getVariableNames()) {
                    ns.setLocalVariable(varName, namespace.getVariable(varName, false), false);
                }
                for (KrineMethod method : namespace.getMethods()) {
                    ns.setMethod(method);
                }
            } catch (UtilEvalException e) {
                throw e.toEvalError(SimpleNode.JAVA_CODE, callStack);
            }
            return ns.getThis(declaringKrineBasicInterpreter);
        }

        // Look for a default invoke() handler method in the nameSpace
        // Note: this code duplicates that in NameSpace getCommand()
        // is that ok?
        try {
            krineMethod = namespace.getMethod(
                    "invoke", new Class[]{null, null});
        } catch (UtilEvalException e) { /*leave null*/ }

        // Call script "invoke( String methodName, Object [] args );
        if (krineMethod != null)
            return krineMethod.invoke(new Object[]{methodName, args},
                    krineBasicInterpreter, callStack, callerInfo);

        throw new EvalError("Method " +
                StringUtil.methodString(methodName, types) +
                " not found in object: " + namespace.getName(),
                callerInfo, callStack);
    }

    /**
     * This is the invocation handler for the dynamic proxy.
     * <p>
     * <p>
     * Notes:
     * Inner class for the invocation handler seems to shield this unavailable
     * interface from JDK1.2 VM...
     * <p>
     * I don't understand this.  JThis works just fine even if those
     * classes aren't there (doesn't it?)  This class shouldn't be loaded
     * if an XThis isn't instantiated in NameSpace.java, should it?
     */
    class Handler implements InvocationHandler, java.io.Serializable {
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            try {
                return invokeImpl(proxy, method, args);
            } catch (KrineTargetException te) {
                // Unwrap target exception.  If the interface declares that
                // it throws the ex it will be delivered.  If not it will be
                // wrapped in an UndeclaredThrowable

                // This isn't simple because unwrapping this loses all context info.
                // So rewrap is better than unwrap.  - fschmidt
                Throwable t = te.getTarget();
                Class<? extends Throwable> c = t.getClass();
                String msg = t.getMessage();
                try {
                    Throwable t2 = msg == null
                            ? c.getConstructor().newInstance()
                            : c.getConstructor(String.class).newInstance(msg);
                    t2.initCause(te);
                    throw t2;
                } catch (NoSuchMethodException e) {
                    throw t;
                }
            } catch (EvalError ee) {
                // Ease debugging...
                // XThis.this refers to the enclosing class instance
                if (KrineBasicInterpreter.DEBUG)
                    KrineBasicInterpreter.debug("EvalError in scripted interface: "
                            + This.this.toString() + ": " + ee);
                throw ee;
            }
        }

        public Object invokeImpl(Object proxy, Method method, Object[] args)
                throws EvalError {
            String methodName = method.getName();
//            CallStack callStack = new CallStack(namespace);

			/*
                If equals() is not explicitly defined we must override the
				default implemented by the This object protocol for scripted
				object.  To support XThis equals() must test for equality with
				the generated proxy object, not the scripted krine This object;
				otherwise callers from outside in Java will not see a the
				proxy object as equal to itself.
			*/
            KrineMethod equalsMethod = null;
            try {
                equalsMethod = namespace.getMethod(
                        "equals", new Class[]{Object.class});
            } catch (UtilEvalException e) {/*leave null*/ }
            if (methodName.equals("equals") && equalsMethod == null) {
                Object obj = args[0];
                return proxy == obj;
            }

			/*
                If toString() is not explicitly defined override the default
				to show the proxy interfaces.
			*/
            KrineMethod toStringMethod = null;
            try {
                toStringMethod =
                        namespace.getMethod("toString", new Class[]{});
            } catch (UtilEvalException e) {/*leave null*/ }

            if (methodName.equals("toString") && toStringMethod == null) {
                Class[] ints = proxy.getClass().getInterfaces();
                // XThis.this refers to the enclosing class instance
                StringBuilder sb = new StringBuilder(
                        This.this.toString() + "\nimplements:");
                for (Class anInt : ints)
                    sb.append(" ").append(anInt.getName()).append((ints.length > 1) ? "," : "");
                return sb.toString();
            }

            Class[] paramTypes = method.getParameterTypes();
            return Primitive.unwrap(
                    invokeMethod(methodName, Primitive.wrap(args, paramTypes)));
        }
    }
}

