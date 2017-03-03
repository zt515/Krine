package com.dragon.lang.ast;

import com.dragon.lang.*;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.reflect.ReflectException;
import com.dragon.lang.utils.CollectionManager;

import java.lang.reflect.Field;

/**
 * An LeftValue is a wrapper for an variable, field, or property.  It ordinarily
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
    public NameSpace nameSpace;
    /**
     * The assignment should be to a local variable
     */
    boolean localVar;

    /**
     * Identifiers for the various types of LeftValue.
     */
    public static final int
            VARIABLE = 0;
    static final int FIELD = 1;
    static final int PROPERTY = 2;
    static final int INDEX = 3;
    static final int METHOD_EVAL = 4;

    public int type;

    String varName;
    String propName;
    Field field;
    Object object;
    int index;

    /**
     * Variable LeftValue constructor.
     */
    LeftValue(NameSpace nameSpace, String varName) {
        throw new Error("namespace lhs");
/*
        type = VARIABLE;
		this.varName = varName;
		this.nameSpace = nameSpace;
*/
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
     * Static field LeftValue Constructor.
     * This simply calls Object field constructor with null object.
     */
    public LeftValue(Field field) {
        type = FIELD;
        this.object = null;
        this.field = field;
    }

    /**
     * Object field LeftValue Constructor.
     */
    public LeftValue(Object object, Field field) {
        if (object == null)
            throw new NullPointerException("constructed empty LeftValue");

        type = FIELD;
        this.object = object;
        this.field = field;
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
     * Array index LeftValue Constructor.
     */
    LeftValue(Object array, int index) {
        if (array == null)
            throw new NullPointerException("constructed empty LeftValue");

        type = INDEX;
        this.object = array;
        this.index = index;
    }

    public Object getValue() throws UtilEvalException {
        if (type == VARIABLE)
            return nameSpace.getVariable(varName);

        if (type == FIELD)
            try {
                Object o = field.get(object);
                return Primitive.wrap(o, field.getType());
            } catch (IllegalAccessException e2) {
                throw new UtilEvalException("Can't read field: " + field);
            }

        if (type == PROPERTY)
            try {
                return Reflect.getObjectProperty(object, propName);
            } catch (ReflectException e) {
                DragonBasicInterpreter.debug(e.getMessage());
                throw new UtilEvalException("No such property: " + propName);
            }

        if (type == INDEX)
            try {
                return Reflect.getIndex(object, index);
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
            // Set the variable in namespace according to localVar flag
            if (localVar)
                nameSpace.setLocalVariable(varName, val, strictJava);
            else
                nameSpace.setVariable(varName, val, strictJava);
        } else if (type == FIELD) {
            try {
                Object fieldVal = val instanceof Primitive ?
                        ((Primitive) val).getValue() : val;

                // This should probably be in Reflect.java
                Reflect.setAccessible(field);
                field.set(object, fieldVal);
                return val;
            } catch (NullPointerException e) {
                throw new UtilEvalException(
                        "LeftValue (" + field.getName() + ") not a static field.", e);
            } catch (IllegalAccessException e2) {
                throw new UtilEvalException(
                        "LeftValue (" + field.getName() + ") can't access field: " + e2, e2);
            } catch (IllegalArgumentException e3) {
                String type = val instanceof Primitive ?
                        ((Primitive) val).getType().getName()
                        : val.getClass().getName();
                throw new UtilEvalException(
                        "Argument type mismatch. " + (val == null ? "null" : type)
                                + " not assignable to field " + field.getName());
            }
        } else if (type == PROPERTY) {
            /*
			if ( object instanceof Hashtable )
				((Hashtable)object).put(propName, val);
			*/
            CollectionManager cm = CollectionManager.getCollectionManager();
            if (cm.isMap(object))
                cm.putInMap(object/*map*/, propName, val);
            else
                try {
                    Reflect.setObjectProperty(object, propName, val);
                } catch (ReflectException e) {
                    DragonBasicInterpreter.debug("Assignment: " + e.getMessage());
                    throw new UtilEvalException("No such property: " + propName);
                }
        } else if (type == INDEX)
            try {
                Reflect.setIndex(object, index, val);
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
                + ((field != null) ? "field = " + field.toString() : "")
                + (varName != null ? " varName = " + varName : "")
                + (nameSpace != null ? " nameSpace = " + nameSpace.toString() : "");
    }
}

