package com.krine.lang.reflect;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.UtilTargetException;
import com.krine.lang.ast.*;
import com.krine.lang.classpath.KrineClassManager;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.Capabilities;
import com.krine.lang.utils.StringUtil;

import java.lang.reflect.*;
import java.util.*;

/**
 * All of the reflection API code lies here.  It is in the form of static
 * utilities.  Maybe this belongs in LeftValue.java or a generic object
 * wrapper class.
 * Note: This class is messy.  The method and field resolution need to be
 * rewritten.  Various methods in here catch NoSuchMethod or NoSuchField
 * exceptions during their searches.  These should be rewritten to avoid
 * having to catch the exceptions.  Method lookups are now cached at a high
 * level so they are less important, however the logic is messy.
 */
public final class Reflect {


    /**
     * A comparator which sorts methods according to {@link #getVisibility}.
     */
    public static final Comparator<Method> METHOD_COMPARATOR = new Comparator<Method>() {
        public int compare(final Method a, final Method b) {
            final int scoreA = getVisibility(a);
            final int scoreB = getVisibility(b);
            return (scoreA < scoreB) ? -1 : ((scoreA == scoreB) ? 0 : 1);

        }
    };


    /**
     * Invoke method on arbitrary object instance.
     * invocation may be static (through the object instance) or dynamic.
     * Object may be a krine scripted object (krine.This type).
     *
     * @return the result of the method call
     */
    public static Object invokeObjectMethod(Object object, String methodName, Object[] args, KrineBasicInterpreter krineBasicInterpreter, CallStack callStack, SimpleNode callerInfo) throws ReflectException, EvalError, InvocationTargetException {
        // Krine scripted object
        if (object instanceof This && !This.isExposedThisMethod(methodName)) {
            return ((This) object).invokeMethod(methodName, args, krineBasicInterpreter, callStack, callerInfo, false/*declaredOnly*/);
        }

        // Plain Java object, find the java method
        try {
            KrineClassManager dcm = krineBasicInterpreter == null ? null : krineBasicInterpreter.getClassManager();
            Class clazz = object.getClass();

            Method method = resolveExpectedJavaMethod(dcm, clazz, object, methodName, args, false);

            return invokeMethod(method, object, args);
        } catch (UtilEvalException e) {
            throw e.toEvalError(callerInfo, callStack);
        }
    }


    /**
     * Invoke a method known to be static.
     * No object instance is needed and there is no possibility of the
     * method being a krine scripted method.
     */
    public static Object invokeStaticMethod(KrineClassManager dcm, Class clazz, String methodName, Object[] args) throws ReflectException, UtilEvalException, InvocationTargetException {
        KrineBasicInterpreter.debug("invoke static Method");
        Method method = resolveExpectedJavaMethod(dcm, clazz, null, methodName, args, true);
        return invokeMethod(method, null, args);
    }


    /**
     * Invoke the Java method on the specified object, performing needed
     * type mappings on arguments and return values.
     *
     * @param args may be null
     */
    public static Object invokeMethod(Method method, Object object, Object[] args) throws ReflectException, InvocationTargetException {
        if (args == null) {
            args = new Object[0];
        }

        logInvokeMethod("Invoking method (entry): ", method, args);

        boolean isVarArgs = method.isVarArgs();

        // Map types to assignable forms, need to keep this fast...
        Class<?>[] types = method.getParameterTypes();
        Object[] tmpArgs = new Object[types.length];
        int fixedArgLen = types.length;
        if (isVarArgs) {
            if (fixedArgLen == args.length && types[fixedArgLen - 1].isAssignableFrom(args[fixedArgLen - 1].getClass())) {
                isVarArgs = false;
            } else {
                fixedArgLen--;
            }
        }
        try {
            for (int i = 0; i < fixedArgLen; i++) {
                tmpArgs[i] = Types.castObject(args[i]/*rhs*/, types[i]/*lhsType*/, Types.ASSIGNMENT);
            }
            if (isVarArgs) {
                Class varType = types[fixedArgLen].getComponentType();
                Object varArgs = Array.newInstance(varType, args.length - fixedArgLen);
                for (int i = fixedArgLen, j = 0; i < args.length; i++, j++) {
                    Array.set(varArgs, j, Primitive.unwrap(Types.castObject(args[i]/*rhs*/, varType/*lhsType*/, Types.ASSIGNMENT)));
                }
                tmpArgs[fixedArgLen] = varArgs;
            }
        } catch (UtilEvalException e) {
            throw new InterpreterException("illegal argument type in method invocation: " + e);
        }

        // unwrap any primitives
        tmpArgs = Primitive.unwrap(tmpArgs);

        logInvokeMethod("Invoking method (after massaging values): ", method, tmpArgs);

        try {
            Object returnValue = method.invoke(object, tmpArgs);
            if (returnValue == null) {
                returnValue = Primitive.NULL;
            }
            Class returnType = method.getReturnType();

            return Primitive.wrap(returnValue, returnType);
        } catch (IllegalAccessException e) {
            throw new ReflectException("Cannot access method " + StringUtil.methodString(method.getName(), method.getParameterTypes()) + " in '" + method.getDeclaringClass() + "' :" + e, e);
        }
    }


    public static Object getIndex(Object array, int index) throws ReflectException, UtilTargetException {
        if (KrineBasicInterpreter.DEBUG) {
            KrineBasicInterpreter.debug("getIndex: " + array + ", index=" + index);
        }
        try {
            Object val = Array.get(array, index);
            return Primitive.wrap(val, array.getClass().getComponentType());
        } catch (ArrayIndexOutOfBoundsException e1) {
            throw new UtilTargetException(e1);
        } catch (Exception e) {
            throw new ReflectException("Array access:" + e);
        }
    }


    public static void setIndex(Object array, int index, Object val) throws ReflectException, UtilTargetException {
        try {
            val = Primitive.unwrap(val);
            Array.set(array, index, val);
        } catch (ArrayStoreException e2) {
            throw new UtilTargetException(e2);
        } catch (IllegalArgumentException e1) {
            //noinspection ThrowableInstanceNeverThrown
            throw new UtilTargetException(new ArrayStoreException(e1.toString()));
        } catch (Exception e) {
            throw new ReflectException("Array access:" + e);
        }
    }


    public static Object getStaticFieldValue(Class clazz, String fieldName) throws UtilEvalException, ReflectException {
        return getFieldValue(clazz, null, fieldName, true/*onlyStatic*/);
    }


    public static Object getObjectFieldValue(Object object, String fieldName) throws UtilEvalException, ReflectException {
        if (object instanceof This) {
            return ((This) object).namespace.getVariable(fieldName);
        } else if (object == Primitive.NULL) {
            //noinspection ThrowableInstanceNeverThrown
            throw new UtilTargetException(new NullPointerException("Attempt to access field '" + fieldName + "' on null value"));
        } else {
            try {
                return getFieldValue(object.getClass(), object, fieldName, false/*onlyStatic*/);
            } catch (ReflectException e) {
                // no field, try property access

                if (hasObjectPropertyGetter(object.getClass(), fieldName)) {
                    return getObjectProperty(object, fieldName);
                } else {
                    throw e;
                }
            }
        }
    }


    public static LeftValue getLHSStaticField(Class clazz, String fieldName) throws UtilEvalException, ReflectException {
        Field f = resolveExpectedJavaField(clazz, fieldName, true/*onlyStatic*/);
        return new LeftValue(f);
    }


    /**
     * Get an LeftValue reference to an object field.
     * <p/>
     * This method also deals with the field style property access.
     * In the field does not exist we check for a property setter.
     */
    public static LeftValue getLHSObjectField(Object object, String fieldName) throws UtilEvalException, ReflectException {
        if (object instanceof This) {
            // I guess this is when we pass it as an argument?
            // Setting locally
            boolean recurse = false;
            return new LeftValue(((This) object).namespace, fieldName, recurse);
        }

        try {
            Field f = resolveExpectedJavaField(object.getClass(), fieldName, false/*staticOnly*/);
            return new LeftValue(object, f);
        } catch (ReflectException e) {
            // not a field, try property access
            if (hasObjectPropertySetter(object.getClass(), fieldName)) {
                return new LeftValue(object, fieldName);
            } else {
                throw e;
            }
        }
    }


    private static Object getFieldValue(Class clazz, Object object, String fieldName, boolean staticOnly) throws UtilEvalException, ReflectException {
        try {
            Field f = resolveExpectedJavaField(clazz, fieldName, staticOnly);

            Object value = f.get(object);
            Class returnType = f.getType();
            return Primitive.wrap(value, returnType);

        } catch (NullPointerException e) { // shouldn't happen
            throw new ReflectException(fieldName + " is not a static field.");
        } catch (IllegalAccessException e) {
            throw new ReflectException("Can't access field: " + fieldName);
        }
    }

    /**
     * Note: this method and resolveExpectedJavaField should be rewritten
     * to invert this logic so that no exceptions need to be caught
     * unnecessarily.  This is just a temporary impl.
     *
     * @return the field or null if not found
     */
    public static Field resolveJavaField(Class clazz, String fieldName, boolean staticOnly) throws UtilEvalException {
        try {
            return resolveExpectedJavaField(clazz, fieldName, staticOnly);
        } catch (ReflectException e) {
            return null;
        }
    }


    /**
     * @throws ReflectException if the field is not found.
     *                          Note: this should really just throw NoSuchFieldException... need
     *                          to change related signatures and code.
     */
    protected static Field resolveExpectedJavaField(Class clazz, String fieldName, boolean staticOnly) throws UtilEvalException, ReflectException {
        Field field;
        try {
            if (Capabilities.haveAccessibility()) {
                field = findAccessibleField(clazz, fieldName);
            } else {
                // Class getField() finds only public fields
                field = clazz.getField(fieldName);
            }
        } catch (NoSuchFieldException e) {
            throw new ReflectException("No such field or field isn't accessible: " + fieldName, e);

        } catch (SecurityException e) {
            throw new UtilTargetException("Security Exception while searching fields of: " + clazz, e);
        }

        if (staticOnly && !Modifier.isStatic(field.getModifiers())) {
            throw new UtilEvalException("Can't reach instance field: " + fieldName + " from static context: " + clazz.getName());
        }

        return field;
    }


    /**
     * Used when accessibility capability is available to locate an occurrence
     * of the field in the most derived class or superclass and set its
     * accessibility flag.
     * Note that this method is not needed in the simple non accessible
     * case because we don't have to hunt for fields.
     * Note that classes may declare overlapping private fields, so the
     * distinction about the most derived is important.  Java doesn't normally
     * allow this kind of access (super won't show private variables) so
     * there is no real syntax for specifying which class scope to use...
     *
     * @return the Field or throws NoSuchFieldException
     * @throws NoSuchFieldException if the field is not found
     */
    /*
             This method should be rewritten to use getFields() and avoid catching
			 exceptions during the search.
		 */
    private static Field findAccessibleField(Class clazz, String fieldName) throws UtilEvalException, NoSuchFieldException {
        // Quick check catches public fields include those in interfaces
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            // ignore
        }
        // try hidden fields (protected, private, package protected)
        if (Capabilities.haveAccessibility()) {
            try {
                while (clazz != null) {
                    final Field[] declaredFields = clazz.getDeclaredFields();
                    for (Field field : declaredFields) {
                        if (field.getName().equals(fieldName)) {
                            field.setAccessible(true);
                            return field;
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            } catch (SecurityException e) {
                // ignore -> NoSuchFieldException
            }
        }
        throw new NoSuchFieldException(fieldName);
    }


    /**
     * This method wraps resolveJavaMethod() and expects a non-null method
     * result. If the method is not found it throws a descriptive ReflectException.
     */
    public static Method resolveExpectedJavaMethod(KrineClassManager dcm, Class clazz, Object object, String name, Object[] args, boolean staticOnly) throws ReflectException, UtilEvalException {
        if (object == Primitive.NULL) {
            //noinspection ThrowableInstanceNeverThrown
            throw new UtilTargetException(new NullPointerException("Attempt to invoke method " + name + " on null value"));
        }

        Class[] types = Types.getTypes(args);
        Method method = resolveJavaMethod(dcm, clazz, name, types, staticOnly);

        if (method == null) {
            throw new ReflectException((staticOnly ? "Static method " : "Method ") + StringUtil.methodString(name, types) + " not found in class'" + clazz.getName() + "'");
        }

        return method;
    }


    /**
     * The full blown resolver method.  All other method invocation methods
     * delegate to this.  The method may be static or dynamic unless
     * staticOnly is set (in which case object may be null).
     * If staticOnly is set then only static methods will be located.
     * <p/>
     * <p/>
     * This method performs caching (caches discovered methods through the
     * class manager and utilizes cached methods.)
     * <p/>
     * <p/>
     * This method determines whether to attempt to use non-public methods
     * based on Capabilities.haveAccessibility() and will set the accessibility
     * flag on the method as necessary.
     * <p/>
     * <p/>
     * If, when directed to find a static method, this method locates a more
     * specific matching instance method it will throw a descriptive exception
     * analogous to the error that the Java compiler would produce.
     * Note: as of 2.0.x this is a problem because there is no way to work
     * around this with a cast.
     * <p/>
     *
     * @param staticOnly The method located must be static, the object param may be null.
     * @return the method or null if no matching method was found.
     */
    public static Method resolveJavaMethod(KrineClassManager dcm, Class clazz, String name, Class[] types, boolean staticOnly) throws UtilEvalException {
        if (clazz == null) {
            throw new InterpreterException("null class");
        }

        // Lookup previously cached method
        Method method = null;
        if (dcm == null) {
            KrineBasicInterpreter.debug("resolveJavaMethod UNOPTIMIZED lookup");
        } else {
            method = dcm.getResolvedMethod(clazz, name, types, staticOnly);
        }

        if (method == null) {
            boolean publicOnly = !Capabilities.haveAccessibility();
            // Searching for the method may, itself be a priviliged action
            try {
                method = findOverloadedMethod(clazz, name, types, publicOnly);
            } catch (SecurityException e) {
                throw new UtilTargetException("Security Exception while searching methods of: " + clazz, e);
            }

            checkFoundStaticMethod(method, staticOnly, clazz);

            // This is the first time we've seen this method, set accessibility if needed
            if ((method != null) && !isPublic(method)) {
                if (publicOnly) {
                    KrineBasicInterpreter.debug("resolveJavaMethod - no accessible method found");
                    method = null;
                } else {
                    KrineBasicInterpreter.debug("resolveJavaMethod - setting method accessible");
                    try {
                        method.setAccessible(true);
                    } catch (final SecurityException e) {
                        KrineBasicInterpreter.debug("resolveJavaMethod - setting accessible failed: " + e);
                        method = null;
                    }
                }
            }

            // If succeeded cache the resolved method.
            if (method != null && dcm != null) {
                dcm.cacheResolvedMethod(clazz, types, method);
            }
        }

        return method;
    }


    /**
     * Get the candidate methods by searching the class and interface graph
     * of baseClass and resolve the most specific.
     *
     * @return the method or null for not found
     */
    private static Method findOverloadedMethod(final Class baseClass, final String methodName, final Class[] types, final boolean publicOnly) {
        if (KrineBasicInterpreter.DEBUG) {
            KrineBasicInterpreter.debug("Searching for method: " + StringUtil.methodString(methodName, types) + " in '" + baseClass.getName() + "'");
        }
        final List<Method> publicMethods = new ArrayList<>();
        final Collection<Method> nonPublicMethods = publicOnly ? new DummyCollection<>() : new ArrayList<>();
        collectMethods(baseClass, methodName, types.length, publicMethods, nonPublicMethods);
        Collections.sort(publicMethods, METHOD_COMPARATOR);
        Method method = findMostSpecificMethod(types, publicMethods);
        if (method == null) {
            method = findMostSpecificMethod(types, nonPublicMethods);
        }
        return method;
    }


    /**
     * Accumulate all matching methods, including non-public methods in the
     * inheritance tree of provided baseClass.
     * <p/>
     * This method is analogous to Class getMethods() which returns all public
     * methods in the inheritance tree.
     * <p/>
     * In the normal (non-accessible) case this also addresses the problem
     * that arises when a package private class or private inner class
     * implements a public interface or derives from a public type.  In other
     * words, sometimes we'll find public methods that we can't use directly
     * and we have to find the same public method in a parent class or
     * interface.
     */
    private static void collectMethods(final Class baseClass, final String methodName, final int numArgs, final Collection<Method> publicMethods, final Collection<Method> nonPublicMethods) {
        final Class superclass = baseClass.getSuperclass();
        if (superclass != null) {
            collectMethods(superclass, methodName, numArgs, publicMethods, nonPublicMethods);
        }
        final Method[] methods = baseClass.getDeclaredMethods();
        for (final Method m : methods) {
            if (matchesNameAndSignature(m, methodName, numArgs)) {
                if (isPublic(m.getDeclaringClass()) && isPublic(m)) {
                    publicMethods.add(m);
                } else {
                    nonPublicMethods.add(m);
                }
            }
        }
        for (final Class interfaceClass : baseClass.getInterfaces()) {
            collectMethods(interfaceClass, methodName, numArgs, publicMethods, nonPublicMethods);
        }
    }


    private static boolean matchesNameAndSignature(final Method m, final String methodName, final int numArgs) {
        return m.getName().equals(methodName) && (m.isVarArgs() ? m.getParameterTypes().length - 1 <= numArgs : m.getParameterTypes().length == numArgs);
    }


    /**
     * Primary object constructor
     * This method is simpler than those that must resolve general method
     * invocation because constructors are not inherited.
     * <p/>
     * This method determines whether to attempt to use non-public constructors
     * based on Capabilities.haveAccessibility() and will set the accessibility
     * flag on the method as necessary.
     * <p/>
     */
    public static Object constructObject(Class clazz, Object[] args) throws ReflectException, InvocationTargetException {
        if (clazz.isInterface()) {
            throw new ReflectException("Can't create instance of an interface: " + clazz);
        }

        Class[] types = Types.getTypes(args);

        // Find the constructor.
        // (there are no inherited constructors to worry about)
        Constructor[] constructors = Capabilities.haveAccessibility() ? clazz.getDeclaredConstructors() : clazz.getConstructors();

        if (KrineBasicInterpreter.DEBUG) {
            KrineBasicInterpreter.debug("Looking for most specific constructor: " + clazz);
        }
        Constructor con = findMostSpecificConstructor(types, constructors);
        if (con == null) {
            throw cantFindConstructor(clazz, types);
        }

        if (!isPublic(con) && Capabilities.haveAccessibility()) {
            con.setAccessible(true);
        }

        args = Primitive.unwrap(args);
        try {
            return con.newInstance(args);
        } catch (InstantiationException e) {
            throw new ReflectException("Class " + clazz + " is abstract ", e);
        } catch (IllegalAccessException e) {
            throw new ReflectException("We don't have permission to create an instance. Use setAccessibility(true) to enable access.", e);
        } catch (IllegalArgumentException e) {
            throw new ReflectException("The number of arguments was wrong", e);
        }
    }


    /**
     * This method should parallel findMostSpecificMethod()
     * The only reason it can't be combined is that Method and Constructor
     * don't have a common interface for their signatures
     */
    public static Constructor findMostSpecificConstructor(Class[] idealMatch, Constructor[] constructors) {
        int match = findMostSpecificConstructorIndex(idealMatch, constructors);
        return (match == -1) ? null : constructors[match];
    }


    public static int findMostSpecificConstructorIndex(Class[] idealMatch, Constructor[] constructors) {
        Class[][] candidates = new Class[constructors.length][];
        for (int i = 0; i < candidates.length; i++) {
            candidates[i] = constructors[i].getParameterTypes();
        }

        return findMostSpecificSignature(idealMatch, candidates);
    }


    /**
     * Find the best match for signature idealMatch.
     * It is assumed that the methods array holds only valid candidates
     * (e.g. method name and number of args already matched).
     * This method currently does not take into account Java 5 covariant
     * return types... which I think will require that we find the most
     * derived return type of otherwise identical best matches.
     *
     * @param methods the set of candidate method which differ only in the
     *                types of their arguments.
     * @see #findMostSpecificSignature(Class[], Class[][])
     */
    private static Method findMostSpecificMethod(final Class[] idealMatch, final Collection<Method> methods) {
        if (KrineBasicInterpreter.DEBUG) {
            KrineBasicInterpreter.debug("Looking for most specific method");
        }
        // copy signatures into array for findMostSpecificMethod()
        List<Class[]> candidateSigs = new ArrayList<>();
        List<Method> methodList = new ArrayList<>();
        for (Method method : methods) {
            Class[] parameterTypes = method.getParameterTypes();
            methodList.add(method);
            candidateSigs.add(parameterTypes);
            if (method.isVarArgs()) {
                Class[] candidateSig = new Class[idealMatch.length];
                int j = 0;
                for (; j < parameterTypes.length - 1; j++) {
                    candidateSig[j] = parameterTypes[j];
                }
                Class varType = parameterTypes[j].getComponentType();
                for (; j < idealMatch.length; j++) {
                    candidateSig[j] = varType;
                }
                methodList.add(method);
                candidateSigs.add(candidateSig);
            }
        }

        int match = findMostSpecificSignature(idealMatch, candidateSigs.toArray(new Class[candidateSigs.size()][]));
        return match == -1 ? null : methodList.get(match);
    }


    /**
     * Implement JLS 15.11.2
     * Return the index of the most specific arguments match or -1 if no
     * match is found.
     * This method is used by both methods and constructors (which
     * unfortunately don't share a common interface for signature info).
     *
     * @return the index of the most specific candidate
     * <p>
     * Note: Two methods which are equally specific should not be allowed by
     * the Java compiler.  In this case Krine currently chooses the first
     * one it finds.  We could add a test for this case here (I believe) by
     * adding another isSignatureAssignable() in the other direction between
     * the target and "best" match.  If the assignment works both ways then
     * neither is more specific and they are ambiguous.  I'll leave this test
     * out for now because I'm not sure how much another test would impact
     * performance.  Method selection is now cached at a high level, so a few
     * friendly extraneous tests shouldn't be a problem.
     */
    public static int findMostSpecificSignature(Class[] idealMatch, Class[][] candidates) {
        for (int round = Types.FIRST_ROUND_ASSIGNABLE; round <= Types.LAST_ROUND_ASSIGNABLE; round++) {
            Class[] bestMatch = null;
            int bestMatchIndex = -1;

            for (int i = 0; i < candidates.length; i++) {
                Class[] targetMatch = candidates[i];

                // If idealMatch fits targetMatch and this is the first match
                // or targetMatch is more specific than the best match, make it
                // the new best match.
                if (Types.isSignatureAssignable(idealMatch, targetMatch, round) && ((bestMatch == null) || Types.isSignatureAssignable(targetMatch, bestMatch, Types.JAVA_BASE_ASSIGNABLE))) {
                    bestMatch = targetMatch;
                    bestMatchIndex = i;
                }
            }

            if (bestMatch != null) {
                return bestMatchIndex;
            }
        }

        return -1;
    }


    private static String accessorName(String getOrSet, String propName) {
        return getOrSet + String.valueOf(Character.toUpperCase(propName.charAt(0))) + propName.substring(1);
    }


    public static boolean hasObjectPropertyGetter(Class<?> clazz, String propName) {
        if (clazz == Primitive.class) {
            return false;
        }
        String getterName = accessorName("get", propName);
        try {
            clazz.getMethod(getterName);
            return true;
        } catch (NoSuchMethodException e) { /* fall through */ }
        getterName = accessorName("is", propName);
        try {
            Method m = clazz.getMethod(getterName);
            return (m.getReturnType() == Boolean.TYPE);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }


    public static boolean hasObjectPropertySetter(Class clazz, String propName) {
        String setterName = accessorName("set", propName);
        Method[] methods = clazz.getMethods();

        // we don't know the right hand side of the assignment yet.
        // has at least one setter of the right name?
        for (Method method : methods) {
            if (method.getName().equals(setterName)) {
                return true;
            }
        }
        return false;
    }


    public static Object getObjectProperty(Object obj, String propName) throws UtilEvalException, ReflectException {
        Object[] args = new Object[]{};

        KrineBasicInterpreter.debug("property access: ");
        Method method = null;

        Exception e1 = null, e2 = null;
        try {
            String accessorName = accessorName("get", propName);
            method = resolveExpectedJavaMethod(null/*dcm*/, obj.getClass(), obj, accessorName, args, false);
        } catch (Exception e) {
            e1 = e;
        }
        if (method == null) {
            try {
                String accessorName = accessorName("is", propName);
                method = resolveExpectedJavaMethod(null/*dcm*/, obj.getClass(), obj, accessorName, args, false);
                if (method.getReturnType() != Boolean.TYPE) {
                    method = null;
                }
            } catch (Exception e) {
                e2 = e;
            }
        }
        if (method == null) {
            throw new ReflectException("Error in property getter: " + e1 + (e2 != null ? " : " + e2 : ""));
        }

        try {
            return invokeMethod(method, obj, args);
        } catch (InvocationTargetException e) {
            throw new UtilEvalException("Property accessor threw exception: " + e.getTargetException());
        }
    }


    public static void setObjectProperty(Object obj, String propName, Object value) throws ReflectException, UtilEvalException {
        String accessorName = accessorName("set", propName);
        Object[] args = new Object[]{value};

        KrineBasicInterpreter.debug("property access: ");
        try {
            Method method = resolveExpectedJavaMethod(null/*dcm*/, obj.getClass(), obj, accessorName, args, false);
            invokeMethod(method, obj, args);
        } catch (InvocationTargetException e) {
            throw new UtilEvalException("Property accessor threw exception: " + e.getTargetException());
        }
    }


    /**
     * Return a more human readable version of the type name.
     * Specifically, array types are returned with postfix "[]" dimensions.
     * e.g. return "int []" for integer array instead of "class [I" as
     * would be returned by Class getName() in that case.
     */
    public static String normalizeClassName(Class type) {
        if (!type.isArray()) {
            return type.getName();
        }
        StringBuilder className = new StringBuilder();
        try {
            className.append(getArrayBaseType(type).getName()).append(' ');
            for (int i = 0; i < getArrayDimensions(type); i++) {
                className.append("[]");
            }
        } catch (ReflectException e) {
            /*shouldn't happen*/
        }

        return className.toString();
    }


    /**
     * returns the dimensionality of the Class
     * returns 0 if the Class is not an array class
     */
    public static int getArrayDimensions(Class arrayClass) {
        if (!arrayClass.isArray()) {
            return 0;
        }

        return arrayClass.getName().lastIndexOf('[') + 1;  // why so cute?
    }


    /**
     * Returns the base type of an array Class.
     * throws ReflectException if the Class is not an array class.
     */
    public static Class getArrayBaseType(Class arrayClass) throws ReflectException {
        if (!arrayClass.isArray()) {
            throw new ReflectException("The class is not an array.");
        }

        return arrayClass.getComponentType();

    }


    /**
     * A method may be implemented as a compiled Java class containing one or
     * more static invoke() methods of the correct signature.  The invoke()
     * methods must accept two additional leading arguments of the krineBasicInterpreter
     * and callStack, respectively. e.g. invoke(krineBasicInterpreter, callStack, ... )
     * This method adds the arguments and invokes the static method, returning
     * the result.
     */
    public static Object invokeCompiledMethod(Class commandClass, Object[] args, KrineBasicInterpreter krineBasicInterpreter, CallStack callStack) throws UtilEvalException {
        // add interpreter and namespace to args list
        Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = krineBasicInterpreter;
        invokeArgs[1] = callStack;
        System.arraycopy(args, 0, invokeArgs, 2, args.length);
        KrineClassManager dcm = krineBasicInterpreter.getClassManager();
        try {
            return Reflect.invokeStaticMethod(dcm, commandClass, "invoke", invokeArgs);
        } catch (InvocationTargetException e) {
            throw new UtilEvalException("Error in compiled method: " + e.getTargetException(), e);
        } catch (ReflectException e) {
            throw new UtilEvalException("Error invoking compiled method: " + e, e);
        }
    }


    private static void logInvokeMethod(String msg, Method method, Object[] args) {
        if (KrineBasicInterpreter.DEBUG) {
            KrineBasicInterpreter.debug(msg + method + " with args:");
            for (int i = 0; i < args.length; i++) {
                final Object arg = args[i];
                KrineBasicInterpreter.debug("args[" + i + "] = " + arg + " type = " + (arg == null ? "<unknown>" : arg.getClass()));
            }
        }
    }


    private static void checkFoundStaticMethod(Method method, boolean staticOnly, Class clazz) throws UtilEvalException {
        // We're looking for a static method but found an instance method
        if (method != null && staticOnly && !isStatic(method)) {
            throw new UtilEvalException("Cannot reach instance method: " + StringUtil.methodString(method.getName(), method.getParameterTypes()) + " from static context: " + clazz.getName());
        }
    }


    private static ReflectException cantFindConstructor(Class clazz, Class[] types) {
        if (types.length == 0) {
            return new ReflectException("Can't find default constructor for: " + clazz);
        } else {
            return new ReflectException("Can't find constructor: " + StringUtil.methodString(clazz.getName(), types) + " in class: " + clazz.getName());
        }
    }


    private static boolean isPublic(Member member) {
        return Modifier.isPublic(member.getModifiers());
    }


    private static boolean isPublic(Class clazz) {
        return Modifier.isPublic(clazz.getModifiers());
    }


    private static boolean isStatic(Method m) {
        return Modifier.isStatic(m.getModifiers());
    }


    public static void setAccessible(final Field field) {
        if (!isPublic(field) && Capabilities.haveAccessibility()) {
            field.setAccessible(true);
        }
    }


    /**
     * A method from a non public class gets a visibility score of 0.
     * A method from a public class gets a visibility score of 1.
     * And an interface method will get a visibility score of 2.
     */
    private static int getVisibility(final Method method) {
        final Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass.isInterface()) {
            return 2; // interface methods are always public
        }
        if (isPublic(declaringClass)) {
            return 1;
        }
        return 0;
    }


    private static class DummyCollection<T> extends AbstractCollection<T> {

        @Override
        public Iterator<T> iterator() {
            return Collections.<T>emptySet().iterator();
        }


        @Override
        public int size() {
            return 0;
        }


        @Override
        public boolean add(final T t) {
            return false;
        }
    }

}