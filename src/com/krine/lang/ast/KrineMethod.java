package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.reflect.ReflectException;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.StringUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This represents an instance of a krine method declaration in a particular
 * namespace.  This is a thin wrapper around the KrineMethodDeclaration
 * with a pointer to the declaring namespace.
 * <p>
 * <p>
 * When a method is located in a subordinate namespace or invoked from an
 * arbitrary namespace it must nontheless execute with its 'super' as the
 * context in which it was declared.
 * <p/>
 */
/*
    Note: this method incorrectly caches the method structure.  It needs to
	be cleared when the classloader changes.
*/
public class KrineMethod
        implements java.io.Serializable {
    /*
        This is the namespace in which the method is set.
        It is a back-reference for the node, which needs to execute under this
        namespace.  It is not necessary to declare this transient, because
        we can only be saved as part of our namespace anyway... (currently).
    */
    NameSpace declaringNameSpace;

    // Begin Method components

    Modifiers modifiers;
    private String name;
    private Class returnType;

    // Arguments
    private String[] paramNames;
    private int numArgs;
    private Class[] paramTypes;

    // Scripted method body
    KrineBlock methodBody;

    // Java Method, for a KrineObject that delegates to a real Java method
    private Method javaMethod;
    private Object javaObject;

    // End method components

    public KrineMethod(
            KrineMethodDeclaration method,
            NameSpace declaringNameSpace, Modifiers modifiers) {
        this(method.name, method.returnType, method.paramsNode.getParamNames(),
                method.paramsNode.paramTypes, method.blockNode, declaringNameSpace,
                modifiers);
    }

    public KrineMethod(
            String name, Class returnType, String[] paramNames,
            Class[] paramTypes, KrineBlock methodBody,
            NameSpace declaringNameSpace, Modifiers modifiers
    ) {
        this.name = name;
        this.returnType = returnType;
        this.paramNames = paramNames;
        if (paramNames != null)
            this.numArgs = paramNames.length;
        this.paramTypes = paramTypes;
        this.methodBody = methodBody;
        this.declaringNameSpace = declaringNameSpace;
        this.modifiers = modifiers;
    }

    /*
        Create a KrineMethod that delegates to a real Java method upon invocation.
        This is used to represent imported object methods.
    */
    public KrineMethod(Method method, Object object) {
        this(method.getName(), method.getReturnType(), null/*paramNames*/,
                method.getParameterTypes(), null/*method.block*/,
                null/*declaringNameSpace*/, null/*modifiers*/);

        this.javaMethod = method;
        this.javaObject = object;
    }

    /**
     * Get the argument types of this method.
     * loosely typed (untyped) arguments will be represented by null argument
     * types.
     */
    /*
        Note: krineMethod needs to re-evaluate arg types here
		This is broken.
	*/
    public Class[] getParameterTypes() {
        return paramTypes;
    }

    public String[] getParameterNames() {
        return paramNames;
    }

    /**
     * Get the return type of the method.
     *
     * @return Returns null for a loosely typed return value,
     * Void.TYPE for a void return type, or the Class of the type.
     */
    public Class getReturnType() {
        return returnType;
    }

    public Modifiers getModifiers() {
        return modifiers;
    }

    public void makePublic() {
        if (modifiers == null) {
            modifiers = new Modifiers();
        }
        if (!modifiers.hasModifier("public")) {
            modifiers.addModifier(Modifiers.METHOD, "public");
        }
    }

    public String getName() {
        return name;
    }

    /**
     * Invoke the declared method with the specified arguments and krineBasicInterpreter
     * reference.  This is the simplest form of invoke() for KrineMethod
     * intended to be used in reflective style access to krine scripts.
     */
    public Object invoke(
            Object[] argValues, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        return invoke(argValues, krineBasicInterpreter, null, null, false);
    }

    /**
     * Invoke the krine method with the specified args, krineBasicInterpreter ref,
     * and callStack.
     * callerInfo is the node representing the method invocation
     * It is used primarily for debugging in order to provide access to the
     * text of the construct that invoked the method through the namespace.
     *
     * @param callerInfo is the Krine AST node representing the method
     *                   invocation.  It is used to print the line number and text of
     *                   errors in EvalError exceptions.  If the node is null here error
     *                   messages may not be able to point to the precise location and text
     *                   of the error.
     * @param callStack  is the callStack.  If callStack is null a new one
     *                   will be created with the declaring namespace of the method on top
     *                   of the stack (i.e. it will look for purposes of the method
     *                   invocation like the method call occurred in the declaring
     *                   (enclosing) namespace in which the method is defined).
     */
    public Object invoke(
            Object[] argValues, KrineBasicInterpreter krineBasicInterpreter, CallStack callStack,
            SimpleNode callerInfo)
            throws EvalError {
        return invoke(argValues, krineBasicInterpreter, callStack, callerInfo, false);
    }

    /**
     * Invoke the krine method with the specified args, krineBasicInterpreter ref,
     * and callStack.
     * callerInfo is the node representing the method invocation
     * It is used primarily for debugging in order to provide access to the
     * text of the construct that invoked the method through the namespace.
     *
     * @param callerInfo        is the Krine AST node representing the method
     *                          invocation.  It is used to print the line number and text of
     *                          errors in EvalError exceptions.  If the node is null here error
     *                          messages may not be able to point to the precise location and text
     *                          of the error.
     * @param callStack         is the callStack.  If callStack is null a new one
     *                          will be created with the declaring namespace of the method on top
     *                          of the stack (i.e. it will look for purposes of the method
     *                          invocation like the method call occurred in the declaring
     *                          (enclosing) namespace in which the method is defined).
     * @param overrideNameSpace When true the method is executed in the namespace on the top of the
     *                          stack instead of creating its own local namespace.  This allows it
     *                          to be used in constructors.
     */
    Object invoke(
            Object[] argValues, KrineBasicInterpreter krineBasicInterpreter, CallStack callStack,
            SimpleNode callerInfo, boolean overrideNameSpace)
            throws EvalError {
        if (argValues != null)
            for (Object argValue : argValues)
                if (argValue == null)
                    throw new Error("HERE!");

        if (javaMethod != null)
            try {
                return Reflect.invokeMethod(
                        javaMethod, javaObject, argValues);
            } catch (ReflectException e) {
                throw new EvalError(
                        "Error invoking Java method: " + e, callerInfo, callStack);
            } catch (InvocationTargetException e2) {
                throw new KrineTargetException(
                        "Exception invoking imported object method.",
                        e2, callerInfo, callStack, true/*isNative*/);
            }

        // is this a synchronized method?
        if (modifiers != null && modifiers.hasModifier("synchronized")) {
            // The lock is our declaring namespace's This reference
            // (the method's 'super').  Or in the case of a class it's the
            // class instance.
            Object lock;
            if (declaringNameSpace.isClass) {
                try {
                    lock = declaringNameSpace.getClassInstance();
                } catch (UtilEvalException e) {
                    throw new InterpreterException(
                            "Can't get class instance for synchronized method.");
                }
            } else
                lock = declaringNameSpace.getThis(krineBasicInterpreter); // ???

            synchronized (lock) {
                return invokeImpl(
                        argValues, krineBasicInterpreter, callStack,
                        callerInfo, overrideNameSpace);
            }
        } else
            return invokeImpl(argValues, krineBasicInterpreter, callStack, callerInfo,
                    overrideNameSpace);
    }

    private Object invokeImpl(
            Object[] argValues, KrineBasicInterpreter krineBasicInterpreter, CallStack callStack,
            SimpleNode callerInfo, boolean overrideNameSpace)
            throws EvalError {
        Class returnType = getReturnType();
        Class[] paramTypes = getParameterTypes();

        // If null callStack
        if (callStack == null)
            callStack = new CallStack(declaringNameSpace);

        if (argValues == null)
            argValues = new Object[]{};

        // Cardinality (number of args) mismatch
        if (argValues.length != numArgs) {
            throw new EvalError(
                    "Wrong number of arguments for local method: "
                            + name, callerInfo, callStack);
        }

        // Make the local namespace for the method invocation
        NameSpace localNameSpace;
        if (overrideNameSpace)
            localNameSpace = callStack.top();
        else {
            localNameSpace = new NameSpace(declaringNameSpace, name);
            localNameSpace.isMethod = true;
        }
        // should we do this for both cases above?
        localNameSpace.setNode(callerInfo);

        // set the method parameters in the local namespace
        for (int i = 0; i < numArgs; i++) {
            // Set typed variable
            if (paramTypes[i] != null) {
                try {
                    argValues[i] =
                            //Types.getAssignableForm( argValues[i], paramTypes[i] );
                            Types.castObject(argValues[i], paramTypes[i], Types.ASSIGNMENT);
                } catch (UtilEvalException e) {
                    throw new EvalError(
                            "Invalid argument: "
                                    + "`" + paramNames[i] + "'" + " for method: "
                                    + name + " : " +
                                    e.getMessage(), callerInfo, callStack);
                }
                try {
                    localNameSpace.setTypedVariable(paramNames[i],
                            paramTypes[i], argValues[i], null/*modifiers*/);
                } catch (UtilEvalException e2) {
                    throw e2.toEvalError("Typed method parameter assignment",
                            callerInfo, callStack);
                }
            }
            // Set untyped variable
            else  // untyped param
            {
                // getAssignable would catch this for typed param
                if (argValues[i] == Primitive.VOID)
                    throw new EvalError(
                            "Undefined variable or class name, parameter: " +
                                    paramNames[i] + " to method: "
                                    + name, callerInfo, callStack);
                else
                    try {
                        localNameSpace.setLocalVariable(
                                paramNames[i], argValues[i],
                                krineBasicInterpreter.isStrictJava());
                    } catch (UtilEvalException e3) {
                        throw e3.toEvalError(callerInfo, callStack);
                    }
            }
        }

        // Push the new namespace on the call stack
        if (!overrideNameSpace)
            callStack.push(localNameSpace);

        // Invoke the block, overriding namespace with localNameSpace
        Object ret = methodBody.eval(
                callStack, krineBasicInterpreter, true/*override*/);

        // save the callStack including the called method, just for error mess
        CallStack returnStack = callStack.copy();

        // Get back to caller namespace
        if (!overrideNameSpace)
            callStack.pop();

        ReturnControl retControl = null;
        if (ret instanceof ReturnControl) {
            retControl = (ReturnControl) ret;

            // Method body can only use 'return' statement type return control.
            if (retControl.kind == ParserConstants.RETURN)
                ret = ((ReturnControl) ret).value;
            else
                // retControl.returnPoint is the Node of the return statement
                throw new EvalError("'continue' or 'break' in method body",
                        retControl.returnPoint, returnStack);

            // Check for explicit return of value from void method type.
            // retControl.returnPoint is the Node of the return statement
            if (returnType == Void.TYPE && ret != Primitive.VOID)
                throw new EvalError("Cannot return value from void method",
                        retControl.returnPoint, returnStack);
        }

        if (returnType != null) {
            // If return type void, return void as the value.
            if (returnType == Void.TYPE)
                return Primitive.VOID;

            // return type is a class
            try {
                ret =
                        // Types.getAssignableForm( ret, (Class)returnType );
                        Types.castObject(ret, returnType, Types.ASSIGNMENT);
            } catch (UtilEvalException e) {
                // Point to return statement point if we had one.
                // (else it was implicit return? What's the case here?)
                SimpleNode node = callerInfo;
                if (retControl != null)
                    node = retControl.returnPoint;
                throw e.toEvalError(
                        "Incorrect type returned from method: "
                                + name + e.getMessage(), node, callStack);
            }
        }

        return ret;
    }

    public boolean hasModifier(String name) {
        return modifiers != null && modifiers.hasModifier(name);
    }

    public String toString() {
        return "Scripted Method: "
                + StringUtil.methodString(name, getParameterTypes());
    }

    // equal signature
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (o.getClass() == this.getClass()) {
            KrineMethod m = (KrineMethod) o;
            if (!name.equals(m.name) || numArgs != m.numArgs)
                return false;
            for (int i = 0; i < numArgs; i++) {
                if (!equal(paramTypes[i], m.paramTypes[i]))
                    return false;
            }
            return true;
        }
        return false;
    }


    private static boolean equal(Object obj1, Object obj2) {
        return obj1 == null ? obj2 == null : obj1.equals(obj2);
    }


    @Override
    public int hashCode() {
        int h = name.hashCode();
        for (Class<?> paramType : paramTypes) {
            h = h * 31 + paramType.hashCode();
        }
        return h;
    }
}
