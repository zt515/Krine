package com.dragon.lang.ast;

import com.dragon.lang.*;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.reflect.ReflectException;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.utils.CollectionManager;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

class DragonPrimarySuffix extends SimpleNode {
    public static final int
            CLASS = 0,
            INDEX = 1,
            NAME = 2,
            PROPERTY = 3;

    public int operation;
    Object index;
    public String field;

    DragonPrimarySuffix(int id) {
        super(id);
    }

    /*
        Perform a suffix operation on the given object and return the
        new value.
        <p>

        obj will be a Node when suffix evaluation begins, allowing us to
        interpret it contextually. (e.g. for .class) Thereafter it will be
        an value object or LeftValue (as determined by toLeftValue).
        <p>

        We must handle the toLeftValue case at each point here.
        <p>
    */
    public Object doSuffix(
            Object obj, boolean toLHS,
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        // Handle ".class" suffix operation
        // Prefix must be a DragonType
        if (operation == CLASS)
            if (obj instanceof DragonType) {
                if (toLHS)
                    throw new EvalError("Can't assign .class",
                            this, callstack);
                NameSpace namespace = callstack.top();
                return ((DragonType) obj).getType(callstack, dragonBasicInterpreter);
            } else
                throw new EvalError(
                        "Attempt to use .class suffix on non class.",
                        this, callstack);

		/*
            Evaluate our prefix if it needs evaluating first.
			If this is the first evaluation our prefix mayb be a Node 
			(directly from the PrimaryPrefix) - eval() it to an object.  
			If it's an LeftValue, resolve to a value.

			Note: The ambiguous name construct is now necessary where the node 
			may be an ambiguous name.  If this becomes common we might want to 
			make a static method nodeToObject() or something.  The point is 
			that we can't just eval() - we need to direct the evaluation to 
			the context sensitive type of result; namely object, class, etc.
		*/
        if (obj instanceof SimpleNode)
            if (obj instanceof DragonAmbiguousName)
                obj = ((DragonAmbiguousName) obj).toObject(callstack, dragonBasicInterpreter);
            else
                obj = ((SimpleNode) obj).eval(callstack, dragonBasicInterpreter);
        else if (obj instanceof LeftValue)
            try {
                obj = ((LeftValue) obj).getValue();
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callstack);
            }

        try {
            switch (operation) {
                case INDEX:
                    return doIndex(obj, toLHS, callstack, dragonBasicInterpreter);

                case NAME:
                    return doName(obj, toLHS, callstack, dragonBasicInterpreter);

                case PROPERTY:
                    return doProperty(toLHS, obj, callstack, dragonBasicInterpreter);

                default:
                    throw new InterpreterException("Unknown suffix type");
            }
        } catch (ReflectException e) {
            throw new EvalError("reflection error: " + e, this, callstack, e);
        } catch (InvocationTargetException e) {
            throw new DragonTargetException("target exception", e.getTargetException(),
                    this, callstack, true);
        }
    }

    /*
        Field access, .length on array, or a method invocation
        Must handle toLeftValue case for each.
    */
    private Object doName(
            Object obj, boolean toLHS,
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError, ReflectException, InvocationTargetException {
        try {
            // .length on array
            if (field.equals("length") && obj.getClass().isArray())
                if (toLHS)
                    throw new EvalError(
                            "Can't assign array length", this, callstack);
                else
                    return new Primitive(Array.getLength(obj));

            // field access
            if (jjtGetNumChildren() == 0)
                if (toLHS)
                    return Reflect.getLHSObjectField(obj, field);
                else
                    return Reflect.getObjectFieldValue(obj, field);

            // Method invocation
            // (LeftValue or non LeftValue evaluation can both encounter method calls)
            Object[] oa = ((DragonArguments) jjtGetChild(0)).getArguments(
                    callstack, dragonBasicInterpreter);

            // TODO:
            // Note: this try/catch block is copied from DragonMethodInvocation
            // we need to factor out this common functionality and make sure
            // we handle all cases ... (e.g. property style access, etc.)
            // maybe move this to Reflect ?
            try {
                return Reflect.invokeObjectMethod(
                        obj, field, oa, dragonBasicInterpreter, callstack, this);
            } catch (ReflectException e) {
                throw new EvalError(
                        "Error in method invocation: " + e.getMessage(),
                        this, callstack, e);
            } catch (InvocationTargetException e) {
                String msg = "Method Invocation " + field;
                Throwable te = e.getTargetException();

				/*
                    Try to squeltch the native code stack trace if the exception
					was caused by a reflective call back into the dragon dragonBasicInterpreter
					(e.g. eval() or source()
				*/
                boolean isNative = true;
                if (te instanceof EvalError)
                    if (te instanceof DragonTargetException)
                        isNative = ((DragonTargetException) te).exceptionInNative();
                    else
                        isNative = false;

                throw new DragonTargetException(msg, te, this, callstack, isNative);
            }

        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /**
     */
    static int getIndexAux(
            Object obj, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter,
            SimpleNode callerInfo)
            throws EvalError {
        if (!obj.getClass().isArray())
            throw new EvalError("Not an array", callerInfo, callstack);

        int index;
        try {
            Object indexVal =
                    ((SimpleNode) callerInfo.jjtGetChild(0)).eval(
                            callstack, dragonBasicInterpreter);
            if (!(indexVal instanceof Primitive))
                indexVal = Types.castObject(
                        indexVal, Integer.TYPE, Types.ASSIGNMENT);
            index = ((Primitive) indexVal).intValue();
        } catch (UtilEvalException e) {
            DragonBasicInterpreter.debug("doIndex: " + e);
            throw e.toEvalError(
                    "Arrays may only be indexed by integer types.",
                    callerInfo, callstack);
        }

        return index;
    }

    /**
     * array index.
     * Must handle toLeftValue case.
     */
    private Object doIndex(
            Object obj, boolean toLHS,
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError, ReflectException {
        int index = getIndexAux(obj, callstack, dragonBasicInterpreter, this);
        if (toLHS)
            return new LeftValue(obj, index);
        else
            try {
                return Reflect.getIndex(obj, index);
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callstack);
            }
    }

    /**
     * Property access.
     * Must handle toLeftValue case.
     */
    private Object doProperty(boolean toLHS,
                              Object obj, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        if (obj == Primitive.VOID)
            throw new EvalError(
                    "Attempt to access property on undefined variable or class name",
                    this, callstack);

        if (obj instanceof Primitive)
            throw new EvalError("Attempt to access property on a primitive",
                    this, callstack);

        Object value = ((SimpleNode) jjtGetChild(0)).eval(
                callstack, dragonBasicInterpreter);

        if (!(value instanceof String))
            throw new EvalError(
                    "Property expression must be a String or identifier.",
                    this, callstack);

        if (toLHS)
            return new LeftValue(obj, (String) value);

        // Property style access to Hashtable or Map
        CollectionManager cm = CollectionManager.getCollectionManager();
        if (cm.isMap(obj)) {
            Object val = cm.getFromMap(obj, value/*key*/);
            return (val == null ? val = Primitive.NULL : val);
        }

        try {
            return Reflect.getObjectProperty(obj, (String) value);
        } catch (UtilEvalException e) {
            throw e.toEvalError("Property: " + value, this, callstack);
        } catch (ReflectException e) {
            throw new EvalError("No such property: " + value, this, callstack);
        }
    }
}

