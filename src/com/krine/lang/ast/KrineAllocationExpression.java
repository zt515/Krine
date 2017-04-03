package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.classpath.ClassIdentifier;
import com.krine.lang.classpath.GeneratedClass;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.reflect.ReflectException;
import com.krine.lang.utils.CallStack;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

/**
 * New object, new array, or inner class style allocation with body.
 */
class KrineAllocationExpression extends SimpleNode {
    KrineAllocationExpression(int id) {
        super(id);
    }

    private static int innerClassCount = 0;

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        // type is either a class name or a primitive type
        SimpleNode type = (SimpleNode) jjtGetChild(0);

        // args is either constructor arguments or array dimensions
        SimpleNode args = (SimpleNode) jjtGetChild(1);

        if (type instanceof KrineAmbiguousName) {
            KrineAmbiguousName name = (KrineAmbiguousName) type;

            if (args instanceof KrineArguments)
                return objectAllocation(name, (KrineArguments) args,
                        callStack, krineBasicInterpreter);
            else
                return objectArrayAllocation(name, (KrineArrayDimensions) args,
                        callStack, krineBasicInterpreter);
        } else
            return primitiveArrayAllocation((KrinePrimitiveType) type,
                    (KrineArrayDimensions) args, callStack, krineBasicInterpreter);
    }

    private Object objectAllocation(
            KrineAmbiguousName nameNode, KrineArguments argumentsNode,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter
    )
            throws EvalError {

        Object[] args = argumentsNode.getArguments(callStack, krineBasicInterpreter);
        if (args == null)
            throw new EvalError("Null args in new.", this, callStack);

        // Look for scripted class object
        @SuppressWarnings("UnusedAssignment")
        Object obj = nameNode.toObject(
                callStack, krineBasicInterpreter, false/* force class*/);

        // Try regular class
        obj = nameNode.toObject(
                callStack, krineBasicInterpreter, true/*force class*/);

        Class type;
        if (obj instanceof ClassIdentifier)
            type = ((ClassIdentifier) obj).getTargetClass();
        else
            throw new EvalError(
                    "Unknown class: " + nameNode.text, this, callStack);

        // Is an inner class style object allocation
        boolean hasBody = jjtGetNumChildren() > 2;

        if (hasBody) {
            KrineBlock body = (KrineBlock) jjtGetChild(2);
            if (type.isInterface())
                return constructWithInterfaceBody(
                        type, args, body, callStack, krineBasicInterpreter);
            else
                return constructWithClassBody(
                        type, args, body, callStack, krineBasicInterpreter);
        } else
            return constructObject(type, args, callStack, krineBasicInterpreter);
    }


    private Object constructObject(Class<?> type, Object[] args, CallStack callStack, KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        final boolean isGeneratedClass = GeneratedClass.class.isAssignableFrom(type);
        if (isGeneratedClass) {
            ClassGenerator.registerConstructorContext(callStack, krineBasicInterpreter);
        }
        Object obj;
        try {
            obj = Reflect.constructObject(type, args);
        } catch (ReflectException e) {
            throw new EvalError("Constructor error: " + e.getMessage(), this, callStack, e);
        } catch (InvocationTargetException e) {
            // No need to wrap this debug
            KrineBasicInterpreter.debug("The constructor threw an exception:\n\t" + e.getTargetException());
            throw new KrineTargetException("Object constructor", e.getTargetException(), this, callStack, true);
        } finally {
            if (isGeneratedClass) {
                ClassGenerator.registerConstructorContext(null, null); // clean up, prevent memory leak
            }
        }

        String className = type.getName();
        // Is it an inner class?
        if (!className.contains("$"))
            return obj;

        // Temporary hack to support inner classes
        // If the obj is a non-static inner class then import the context...
        // This is not a sufficient emulation of inner classes.
        // Replace this later...

        // work through to class 'this'
        This ths = callStack.top().getThis(null);
        NameSpace instanceNameSpace =
                Name.getClassNameSpace(ths.getNameSpace());

        // Change the parent (which was the class static) to the class instance
        // We really need to check if we're a static inner class here first...
        // but for some reason Java won't show the static modifier on our
        // fake inner classes...  could generate a flag leftValue.
        if (instanceNameSpace != null
                && className.startsWith(instanceNameSpace.getName() + "$")
                ) {
            ClassGenerator.getClassGenerator().setInstanceNameSpaceParent(
                    obj, className, instanceNameSpace);
        }

        return obj;
    }

    private Object constructWithClassBody(
            Class type, Object[] args, KrineBlock block,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        String name = callStack.top().getName() + "$" + (++innerClassCount);
        Modifiers modifiers = new Modifiers();
        modifiers.addModifier(Modifiers.CLASS, "public");
        Class clazz = ClassGenerator.getClassGenerator().generateClass(
                name, modifiers, null/*interfaces*/, type/*superClass*/,
                block, false/*isInterface*/, callStack, krineBasicInterpreter);
        try {
            return Reflect.constructObject(clazz, args);
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof InvocationTargetException) {
                cause = ((InvocationTargetException) e).getTargetException();
            }
            throw new EvalError("Error constructing inner class instance: " + e, this, callStack, cause);
        }
    }

    private Object constructWithInterfaceBody(
            Class type, Object[] args, KrineBlock body,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        NameSpace namespace = callStack.top();
        NameSpace local = new NameSpace(namespace, "AnonymousBlock");
        callStack.push(local);
        body.eval(callStack, krineBasicInterpreter, true/*overrideNamespace*/);
        callStack.pop();
        // statical import fields from the interface so that code inside
        // can refer to the fields directly (e.g. HEIGHT)
        local.importStatic(type);
        return local.getThis(krineBasicInterpreter).getInterface(type);
    }

    private Object objectArrayAllocation(
            KrineAmbiguousName nameNode, KrineArrayDimensions dimensionsNode,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter
    )
            throws EvalError {
        NameSpace namespace = callStack.top();
        Class type = nameNode.toClass(callStack, krineBasicInterpreter);
        if (type == null)
            throw new EvalError("Class " + nameNode.getName(namespace)
                    + " not found.", this, callStack);

        return arrayAllocation(dimensionsNode, type, callStack, krineBasicInterpreter);
    }

    private Object primitiveArrayAllocation(
            KrinePrimitiveType typeNode, KrineArrayDimensions dimensionsNode,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter
    )
            throws EvalError {
        Class type = typeNode.getType();

        return arrayAllocation(dimensionsNode, type, callStack, krineBasicInterpreter);
    }

    private Object arrayAllocation(
            KrineArrayDimensions dimensionsNode, Class type,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        /*
            dimensionsNode can return either a fully initialized array or VOID.
			when VOID the prescribed array dimensions (defined and undefined)
			are contained in the node.
		*/
        Object result = dimensionsNode.eval(type, callStack, krineBasicInterpreter);
        if (result != Primitive.VOID)
            return result;
        else
            return arrayNewInstance(type, dimensionsNode, callStack);
    }

    /**
     * Create an array of the dimensions specified in dimensionsNode.
     * dimensionsNode may contain a number of "undefined" as well as "defined"
     * dimensions.
     * <p>
     * <p>
     * Background: in Java arrays are implemented in arrays-of-arrays style
     * where, for example, a two dimensional array is a an array of arrays of
     * some base type.  Each dimension-type has a Java class type associated
     * with it... so if foo = new int[5][5] then the type of foo is
     * int [][] and the type of foo[0] is int[], etc.  Arrays may also be
     * specified with undefined trailing dimensions - meaning that the lower
     * order arrays are not allocated as objects. e.g.
     * if foo = new int [5][]; then foo[0] == null //true; and can later be
     * assigned with the appropriate type, e.g. foo[0] = new int[5];
     * (See Learning Java, O'Reilly & Associates more background).
     * <p>
     * <p>
     * To create an array with undefined trailing dimensions using the
     * reflection API we must use an array type to represent the lower order
     * (undefined) dimensions as the "base" type for the array creation...
     * Java will then create the correct type by adding the dimensions of the
     * base type to specified allocated dimensions yielding an array of
     * dimensionality base + specified with the base dimensions unallocated.
     * To create the "base" array type we simply create a prototype, zero
     * length in each dimension, array and use it to get its class
     * (Actually, I think there is a way we could do it with Class.forName()
     * but I don't trust this).   The code is simpler than the explanation...
     * see below.
     */
    private Object arrayNewInstance(
            Class type, KrineArrayDimensions dimensionsNode, CallStack callStack)
            throws EvalError {
        if (dimensionsNode.numUndefinedDims > 0) {
            Object proto = Array.newInstance(
                    type, new int[dimensionsNode.numUndefinedDims]); // zeros
            type = proto.getClass();
        }

        try {
            return Array.newInstance(
                    type, dimensionsNode.definedDimensions);
        } catch (NegativeArraySizeException e1) {
            throw new KrineTargetException(e1, this, callStack);
        } catch (Exception e) {
            throw new EvalError("Can't construct primitive array: " +
                    e.getMessage(), this, callStack);
        }
    }
}
