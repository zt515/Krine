package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.reflect.ReflectException;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.CollectionManager;
import com.krine.lang.utils.InvocationUtil;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

class KrinePrimarySuffix extends SimpleNode {
    public static final int
            CLASS = 0,
            INDEX = 1,
            NAME = 2,
            PROPERTY = 3;

    public int operation;
    public String field;

    KrinePrimarySuffix(int id) {
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
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        // Handle ".class" suffix operation
        // Prefix must be a KrineType
        if (operation == CLASS)
            if (obj instanceof KrineType) {
                if (toLHS)
                    throw new EvalError("Can't assign .class",
                            this, callStack);
                NameSpace namespace = callStack.top();
                return ((KrineType) obj).getType(callStack, krineBasicInterpreter);
            } else
                throw new EvalError(
                        "Attempt to use .class suffix on non class.",
                        this, callStack);

		/*
            Evaluate our prefix if it needs evaluating first.
			If this is the first evaluation our prefix maybe be a Node
			(directly from the PrimaryPrefix) - eval() it to an object.  
			If it's an LeftValue, resolve to a value.

			Note: The ambiguous name construct is now necessary where the node 
			may be an ambiguous name.  If this becomes common we might want to 
			make a static method nodeToObject() or something.  The point is 
			that we can't just eval() - we need to direct the evaluation to 
			the context sensitive type of result; namely object, class, etc.
		*/
        if (obj instanceof SimpleNode)
            if (obj instanceof KrineAmbiguousName)
                obj = ((KrineAmbiguousName) obj).toObject(callStack, krineBasicInterpreter);
            else
                obj = ((SimpleNode) obj).eval(callStack, krineBasicInterpreter);
        else if (obj instanceof LeftValue)
            try {
                obj = ((LeftValue) obj).getValue();
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callStack);
            }

        try {
            switch (operation) {
                case INDEX:
                    return doIndex(obj, toLHS, callStack, krineBasicInterpreter);

                case NAME:
                    return doName(obj, toLHS, callStack, krineBasicInterpreter);

                case PROPERTY:
                    return doProperty(toLHS, obj, callStack, krineBasicInterpreter);

                default:
                    throw new InterpreterException("Unknown suffix type");
            }
        } catch (ReflectException e) {
            throw new EvalError("reflection error: " + e, this, callStack, e);
        } catch (InvocationTargetException e) {
            throw new KrineTargetException("target exception", e.getTargetException(),
                    this, callStack, true);
        }
    }

    /*
        Field access, .length on array, or a method invocation
        Must handle toLeftValue case for each.
    */
    private Object doName(
            Object obj, boolean toLHS,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError, ReflectException, InvocationTargetException {
        try {
            // .length on array
            if (field.equals("length") && obj.getClass().isArray())
                if (toLHS)
                    throw new EvalError(
                            "Can't assign array length", this, callStack);
                else
                    return new Primitive(Array.getLength(obj));

            // leftValue access
            if (jjtGetNumChildren() == 0)
                if (toLHS)
                    return Reflect.getLHSObjectField(obj, field);
                else
                    return Reflect.getObjectFieldValue(obj, field);

            // Method invocation
            // (LeftValue or non LeftValue evaluation can both encounter method calls)
            Object[] oa = ((KrineArguments) jjtGetChild(0)).getArguments(
                    callStack, krineBasicInterpreter);

            // TODO:
            // Note: this try/catch block is copied from KrineMethodInvocation
            // we need to factor out this common functionality and make sure
            // we handle all cases ... (e.g. property style access, etc.)
            // maybe move this to Reflect ?
            try {
                return Reflect.invokeObjectMethod(
                        obj, field, oa, krineBasicInterpreter, callStack, this);
            } catch (ReflectException e) {
                throw new EvalError(
                        "Error in method invocation: " + e.getMessage(),
                        this, callStack, e);
            } catch (InvocationTargetException e) {
                throw InvocationUtil.newTargetException("Method Invocation " + field, this, callStack, e);
            }

        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
    }

    /**
     */
    static int getIndexAux(
            Object obj, CallStack callStack, KrineBasicInterpreter krineBasicInterpreter,
            SimpleNode callerInfo)
            throws EvalError {
        if (!obj.getClass().isArray())
            throw new EvalError("Not an array", callerInfo, callStack);

        int index;
        try {
            Object indexVal =
                    ((SimpleNode) callerInfo.jjtGetChild(0)).eval(
                            callStack, krineBasicInterpreter);
            if (!(indexVal instanceof Primitive))
                indexVal = Types.castObject(
                        indexVal, Integer.TYPE, Types.ASSIGNMENT);
            index = ((Primitive) indexVal).intValue();
        } catch (UtilEvalException e) {
            KrineBasicInterpreter.debug("doIndex: " + e);
            throw e.toEvalError(
                    "Arrays may only be indexed by integer types.",
                    callerInfo, callStack);
        }

        return index;
    }

    /**
     * array arrayIndex.
     * Must handle toLeftValue case.
     */
    private Object doIndex(
            Object obj, boolean toLHS,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError, ReflectException {
        int index = getIndexAux(obj, callStack, krineBasicInterpreter, this);
        if (toLHS)
            return new LeftValue(obj, index);
        else
            try {
                return Reflect.getIndex(obj, index);
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callStack);
            }
    }

    /**
     * Property access.
     * Must handle toLeftValue case.
     */
    private Object doProperty(boolean toLHS,
                              Object obj, CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        if (obj == Primitive.VOID)
            throw new EvalError(
                    "Attempt to access property on undefined variable or class name",
                    this, callStack);

        if (obj instanceof Primitive)
            throw new EvalError("Attempt to access property on a primitive",
                    this, callStack);

        Object value = ((SimpleNode) jjtGetChild(0)).eval(
                callStack, krineBasicInterpreter);

        if (!(value instanceof String))
            throw new EvalError(
                    "Property expression must be a String or identifier.",
                    this, callStack);

        if (toLHS)
            return new LeftValue(obj, (String) value);

        // Property style access to HashTable or Map
        CollectionManager cm = CollectionManager.getCollectionManager();
        if (cm.isMap(obj)) {
            Object val = cm.getFromMap(obj, value/*key*/);
            return (val == null ? val = Primitive.NULL : val);
        }

        try {
            return Reflect.getObjectProperty(obj, (String) value);
        } catch (UtilEvalException e) {
            throw e.toEvalError("Property: " + value, this, callStack);
        } catch (ReflectException e) {
            throw new EvalError("No such property: " + value, this, callStack);
        }
    }
}

