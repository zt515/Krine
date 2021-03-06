package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.UtilTargetException;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.reflect.ReflectException;
import com.krine.lang.utils.CollectionManager;

import java.lang.reflect.Field;

/**
 * An LeftValue is a wrapper for an variable, leftValue, or property.  It ordinarily
 * holds the "left hand side" of an assignment and may be either resolved to
 * a value or assigned a value.
 * <p>
 * <p>
 * There is one special case here termed METHOD_EVAL where the LeftValue is used
 * in an intermediate evaluation of a chain of suffixes and wraps a method
 * invocation.  In this case it may only be resolved to a value and cannot be
 * assigned.  (You can't assign a value to the result of a method call e.g.
 * "foo() = 5;").
 * <p>
 */
public class LeftValue implements ParserConstants, java.io.Serializable {
    /**
     * Identifiers for the various types of LeftValue.
     */
    public static final int
            VARIABLE = 0;
    static final int FIELD = 1;
    static final int PROPERTY = 2;
    static final int INDEX = 3;
    static final int METHOD_EVAL = 4;
    public NameSpace nameSpace;
    public int type;
    /**
     * The assignment should be to a local variable
     */
    boolean localVar;
    String varName;
    String propName;
    Field leftValue;
    Object object;
    int arrayIndex;

    /**
     * Variable LeftValue constructor.
     */
    LeftValue(NameSpace nameSpace, String varName) {
        throw new Error("NameSpace lhs");
    }

    /**
     * @param localVar if true the variable is set directly in the This
     *                 reference's local scope.  If false recursion to look for the variable
     *                 definition in parent's scope is allowed. (e.g. the default case for
     *                 undefined vars going to global).
     */
    public LeftValue(NameSpace nameSpace, String varName, boolean localVar) {
        type = VARIABLE;
        this.localVar = localVar;
        this.varName = varName;
        this.nameSpace = nameSpace;
    }

    /**
     * Static leftValue LeftValue Constructor.
     * This simply calls Object leftValue constructor with null object.
     */
    public LeftValue(Field leftValue) {
        type = FIELD;
        this.object = null;
        this.leftValue = leftValue;
    }

    /**
     * Object leftValue LeftValue Constructor.
     */
    public LeftValue(Object object, Field leftValue) {
        if (object == null)
            throw new NullPointerException("constructed empty LeftValue");

        type = FIELD;
        this.object = object;
        this.leftValue = leftValue;
    }

    /**
     * Object property LeftValue Constructor.
     */
    public LeftValue(Object object, String propName) {
        if (object == null)
            throw new NullPointerException("constructed empty LeftValue");

        type = PROPERTY;
        this.object = object;
        this.propName = propName;
    }

    /**
     * Array arrayIndex LeftValue Constructor.
     */
    LeftValue(Object array, int arrayIndex) {
        if (array == null)
            throw new NullPointerException("constructed empty LeftValue");

        type = INDEX;
        this.object = array;
        this.arrayIndex = arrayIndex;
    }

    public Object getValue() throws UtilEvalException {
        if (type == VARIABLE)
            return nameSpace.getVariable(varName);

        if (type == FIELD)
            try {
                Object o = leftValue.get(object);
                return Primitive.wrap(o, leftValue.getType());
            } catch (IllegalAccessException e2) {
                throw new UtilEvalException("Can't read leftValue: " + leftValue);
            }

        if (type == PROPERTY)
            try {
                return Reflect.getObjectProperty(object, propName);
            } catch (ReflectException e) {
                KrineBasicInterpreter.debug(e.getMessage());
                throw new UtilEvalException("No such property: " + propName);
            }

        if (type == INDEX)
            try {
                return Reflect.getIndex(object, arrayIndex);
            } catch (Exception e) {
                throw new UtilEvalException("Array access: " + e);
            }

        throw new InterpreterException("LeftValue type");
    }

    /**
     * Assign a value to the LeftValue.
     */
    public Object assign(Object val, boolean strictJava)
            throws UtilEvalException {
        if (type == VARIABLE) {
            // Set the variable in nameSpace according to localVar flag
            if (localVar)
                nameSpace.setLocalVariable(varName, val, strictJava);
            else
                nameSpace.setVariable(varName, val, strictJava);
        } else if (type == FIELD) {
            try {
                Object fieldVal = val instanceof Primitive ?
                        ((Primitive) val).getValue() : val;

                // This should probably be in Reflect.java
                Reflect.setAccessible(leftValue);
                leftValue.set(object, fieldVal);
                return val;
            } catch (NullPointerException e) {
                throw new UtilEvalException(
                        "LeftValue (" + leftValue.getName() + ") not a static leftValue.", e);
            } catch (IllegalAccessException e2) {
                throw new UtilEvalException(
                        "LeftValue (" + leftValue.getName() + ") can't access leftValue: " + e2, e2);
            } catch (IllegalArgumentException e3) {
                String type = val instanceof Primitive ?
                        ((Primitive) val).getType().getName()
                        : val.getClass().getName();
                throw new UtilEvalException(
                        "Argument type mismatch. " + (val == null ? "null" : type)
                                + " not assignable to leftValue " + leftValue.getName());
            }
        } else if (type == PROPERTY) {
            CollectionManager cm = CollectionManager.getCollectionManager();
            if (cm.isMap(object))
                cm.putInMap(object/*map*/, propName, val);
            else
                try {
                    Reflect.setObjectProperty(object, propName, val);
                } catch (ReflectException e) {
                    KrineBasicInterpreter.debug("Assignment: " + e.getMessage());
                    throw new UtilEvalException("No such property: " + propName);
                }
        } else if (type == INDEX)
            try {
                Reflect.setIndex(object, arrayIndex, val);
            } catch (UtilTargetException e1) { // pass along target error
                throw e1;
            } catch (Exception e) {
                throw new UtilEvalException("Assignment: " + e.getMessage());
            }
        else
            throw new InterpreterException("unknown lhs");

        return val;
    }

    public String toString() {
        return "LeftValue: "
                + ((leftValue != null) ? "leftValue = " + leftValue.toString() : "")
                + (varName != null ? " varName = " + varName : "")
                + (nameSpace != null ? " nameSpace = " + nameSpace.toString() : "");
    }
}

