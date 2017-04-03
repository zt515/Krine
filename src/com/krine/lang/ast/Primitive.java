package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.UtilEvalException;
import com.krine.lang.UtilTargetException;
import com.krine.lang.reflect.Reflect;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for primitive types in Krine.  This is package public because it
 * is used in the implementation of some krine commands.
 * <p>
 * See the note in LeftValue.java about wrapping objects.
 */
/*
    Note: this class is final because we may test == Primitive.class in places.
	If we need to change that search for those tests.
*/
public final class Primitive implements ParserConstants, Serializable {

    static final Map<Class, Class> wrapperMap = new HashMap<>();

    static {
        wrapperMap.put(Boolean.TYPE, Boolean.class);
        wrapperMap.put(Byte.TYPE, Byte.class);
        wrapperMap.put(Short.TYPE, Short.class);
        wrapperMap.put(Character.TYPE, Character.class);
        wrapperMap.put(Integer.TYPE, Integer.class);
        wrapperMap.put(Long.TYPE, Long.class);
        wrapperMap.put(Float.TYPE, Float.class);
        wrapperMap.put(Double.TYPE, Double.class);
        wrapperMap.put(Boolean.class, Boolean.TYPE);
        wrapperMap.put(Byte.class, Byte.TYPE);
        wrapperMap.put(Short.class, Short.TYPE);
        wrapperMap.put(Character.class, Character.TYPE);
        wrapperMap.put(Integer.class, Integer.TYPE);
        wrapperMap.put(Long.class, Long.TYPE);
        wrapperMap.put(Float.class, Float.TYPE);
        wrapperMap.put(Double.class, Double.TYPE);
    }

    /**
     * The primitive value stored in its java.lang wrapper class
     */
    private Object value;

    private static class Special implements java.io.Serializable {
        private Special() {
        }

        public static final Special NULL_VALUE = new Special() {
            private Object readResolve() throws ObjectStreamException {
                return Special.NULL_VALUE;
            }
        };
        public static final Special VOID_TYPE = new Special() {
            private Object readResolve() throws ObjectStreamException {
                return Special.VOID_TYPE;
            }
        };
    }

    /*
        NULL means "no value".
        This ia a placeholder for primitive null value.
    */
    public static final Primitive NULL = new Primitive(Special.NULL_VALUE);

    /**
     * VOID means "no type".
     * Strictly speaking, this makes no sense here.  But for practical
     * reasons we'll consider the lack of a type to be a special value.
     */
    public static final Primitive VOID = new Primitive(Special.VOID_TYPE);

    private Object readResolve() throws ObjectStreamException {
        if (value == Special.NULL_VALUE) {
            return Primitive.NULL;
        } else if (value == Special.VOID_TYPE) {
            return Primitive.VOID;
        } else {
            return this;
        }
    }

    // private to prevent invocation with param that isn't a primitive-wrapper
    public Primitive(Object value) {
        if (value == null)
            throw new InterpreterException(
                    "Use Primitive.NULL instead of Primitive(null)");

        if (value != Special.NULL_VALUE
                && value != Special.VOID_TYPE &&
                !isWrapperType(value.getClass())
                )
            throw new InterpreterException("Not a wrapper type: " + value);

        this.value = value;
    }

    public Primitive(boolean value) {
        this(Boolean.valueOf(value));
    }

    public Primitive(byte value) {
        this(new Byte(value));
    }

    public Primitive(short value) {
        this(new Short(value));
    }

    public Primitive(char value) {
        this(new Character(value));
    }

    public Primitive(int value) {
        this(new Integer(value));
    }

    public Primitive(long value) {
        this(new Long(value));
    }

    public Primitive(float value) {
        this(new Float(value));
    }

    public Primitive(double value) {
        this(new Double(value));
    }

    /**
     * Return the primitive value stored in its java.lang wrapper class
     */
    public Object getValue() {
        if (value == Special.NULL_VALUE)
            return null;
        else if (value == Special.VOID_TYPE)
            throw new InterpreterException("attempt to unwrap void type");
        else
            return value;
    }

    public String toString() {
        if (value == Special.NULL_VALUE)
            return "null";
        else if (value == Special.VOID_TYPE)
            return "void";
        else
            return value.toString();
    }

    /**
     * Get the corresponding Java primitive TYPE class for this Primitive.
     *
     * @return the primitive TYPE class type of the value or Void.TYPE for
     * Primitive.VOID or null value for type of Primitive.NULL
     */
    public Class getType() {
        if (this == Primitive.VOID)
            return Void.TYPE;

        // NULL return null as type... we currently use null type to indicate
        // loose typing throughout krine.
        if (this == Primitive.NULL)
            return null;

        return unboxType(value.getClass());
    }

    /**
     * Perform a binary operation on two Primitives or wrapper types.
     * If both original args were Primitives return a Primitive result
     * else it was mixed (wrapper/primitive) return the wrapper type.
     * The exception is for boolean operations where we will return the
     * primitive type either way.
     */
    public static Object binaryOperation(
            Object obj1, Object obj2, int kind)
            throws UtilEvalException {
        // special primitive types
        if (obj1 == NULL || obj2 == NULL)
            throw new UtilEvalException(
                    "Null value or 'null' literal in binary operation");
        if (obj1 == VOID || obj2 == VOID)
            throw new UtilEvalException(
                    "Undefined variable, class, or 'void' literal in binary operation");

        // keep track of the original types
        Class lhsOrgType = obj1.getClass();
        Class rhsOrgType = obj2.getClass();

        // Unwrap primitives
        if (obj1 instanceof Primitive)
            obj1 = ((Primitive) obj1).getValue();
        if (obj2 instanceof Primitive)
            obj2 = ((Primitive) obj2).getValue();

        Object[] operands = promotePrimitives(obj1, obj2);
        Object lhs = operands[0];
        Object rhs = operands[1];

        if (lhs.getClass() != rhs.getClass())
            throw new UtilEvalException("Type mismatch in operator.  "
                    + lhs.getClass() + " cannot be used with " + rhs.getClass());

        Object result;
        try {
            result = binaryOperationImpl(lhs, rhs, kind);
        } catch (ArithmeticException e) {
            throw new UtilTargetException("Arithemetic Exception in binary op", e);
        }

        // If both original args were Primitives return a Primitive result
        // else it was mixed (wrapper/primitive) return the wrapper type
        // Exception is for boolean result, return the primitive
        if ((lhsOrgType == Primitive.class && rhsOrgType == Primitive.class)
                || result instanceof Boolean
                )
            return new Primitive(result);
        else
            return result;
    }

    static Object binaryOperationImpl(Object lhs, Object rhs, int kind)
            throws UtilEvalException {
        if (lhs instanceof Boolean)
            return booleanBinaryOperation((Boolean) lhs, (Boolean) rhs, kind);
        else if (lhs instanceof Integer)
            return intBinaryOperation((Integer) lhs, (Integer) rhs, kind);
        else if (lhs instanceof Long)
            return longBinaryOperation((Long) lhs, (Long) rhs, kind);
        else if (lhs instanceof Float)
            return floatBinaryOperation((Float) lhs, (Float) rhs, kind);
        else if (lhs instanceof Double)
            return doubleBinaryOperation((Double) lhs, (Double) rhs, kind);
        else
            throw new UtilEvalException("Invalid types in binary operator");
    }

    static Boolean booleanBinaryOperation(Boolean B1, Boolean B2, int kind) {
        boolean lhs = B1;
        boolean rhs = B2;

        switch (kind) {
            case EQ:
                return lhs == rhs;

            case NE:
                return lhs != rhs;

            case BOOL_OR:
            case BOOL_ORX:
            case BIT_OR:
                return lhs || rhs;

            case BOOL_AND:
            case BOOL_ANDX:
            case BIT_AND:
                return lhs && rhs;

            case XOR:
                return lhs ^ rhs;

            default:
                throw new InterpreterException("unimplemented binary operator");
        }
    }

    // returns Object covering both Long and Boolean return types
    static Object longBinaryOperation(Long L1, Long L2, int kind) {
        long lhs = L1;
        long rhs = L2;

        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs;

            case GT:
            case GTX:
                return lhs > rhs;

            case EQ:
                return lhs == rhs;

            case LE:
            case LEX:
                return lhs <= rhs;

            case GE:
            case GEX:
                return lhs >= rhs;

            case NE:
                return lhs != rhs;

            // arithmetic
            case PLUS:
                return lhs + rhs;

            case MINUS:
                return lhs - rhs;

            case STAR:
                return lhs * rhs;

            case SLASH:
                return lhs / rhs;

            case MOD:
                return lhs % rhs;

            // bitwise
            case LSHIFT:
            case LSHIFTX:
                return lhs << rhs;

            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
                return lhs >> rhs;

            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                return lhs >>> rhs;

            case BIT_AND:
            case BIT_ANDX:
                return lhs & rhs;

            case BIT_OR:
            case BIT_ORX:
                return lhs | rhs;

            case XOR:
                return lhs ^ rhs;

            default:
                throw new InterpreterException(
                        "Unimplemented binary long operator");
        }
    }

    // returns Object covering both Integer and Boolean return types
    static Object intBinaryOperation(Integer I1, Integer I2, int kind) {
        int lhs = I1;
        int rhs = I2;

        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs;

            case GT:
            case GTX:
                return lhs > rhs;

            case EQ:
                return lhs == rhs;

            case LE:
            case LEX:
                return lhs <= rhs;

            case GE:
            case GEX:
                return lhs >= rhs;

            case NE:
                return lhs != rhs;

            // arithmetic
            case PLUS:
                return lhs + rhs;

            case MINUS:
                return lhs - rhs;

            case STAR:
                return lhs * rhs;

            case SLASH:
                return lhs / rhs;

            case MOD:
                return lhs % rhs;

            // bitwise
            case LSHIFT:
            case LSHIFTX:
                return lhs << rhs;

            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
                return lhs >> rhs;

            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                return lhs >>> rhs;

            case BIT_AND:
            case BIT_ANDX:
                return lhs & rhs;

            case BIT_OR:
            case BIT_ORX:
                return lhs | rhs;

            case XOR:
                return lhs ^ rhs;

            default:
                throw new InterpreterException(
                        "Unimplemented binary integer operator");
        }
    }

    // returns Object covering both Double and Boolean return types
    static Object doubleBinaryOperation(Double D1, Double D2, int kind)
            throws UtilEvalException {
        double lhs = D1;
        double rhs = D2;

        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs;

            case GT:
            case GTX:
                return lhs > rhs;

            case EQ:
                return lhs == rhs;

            case LE:
            case LEX:
                return lhs <= rhs;

            case GE:
            case GEX:
                return lhs >= rhs;

            case NE:
                return lhs != rhs;

            // arithmetic
            case PLUS:
                return lhs + rhs;

            case MINUS:
                return lhs - rhs;

            case STAR:
                return lhs * rhs;

            case SLASH:
                return lhs / rhs;

            case MOD:
                return lhs % rhs;

            // can't shift floating-point values
            case LSHIFT:
            case LSHIFTX:
            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                throw new UtilEvalException("Can't shift doubles");

            default:
                throw new InterpreterException(
                        "Unimplemented binary double operator");
        }
    }

    // returns Object covering both Long and Boolean return types
    static Object floatBinaryOperation(Float F1, Float F2, int kind)
            throws UtilEvalException {
        float lhs = F1;
        float rhs = F2;

        switch (kind) {
            // boolean
            case LT:
            case LTX:
                return lhs < rhs;

            case GT:
            case GTX:
                return lhs > rhs;

            case EQ:
                return lhs == rhs;

            case LE:
            case LEX:
                return lhs <= rhs;

            case GE:
            case GEX:
                return lhs >= rhs;

            case NE:
                return lhs != rhs;

            // arithmetic
            case PLUS:
                return lhs + rhs;

            case MINUS:
                return lhs - rhs;

            case STAR:
                return lhs * rhs;

            case SLASH:
                return lhs / rhs;

            case MOD:
                return lhs % rhs;

            // can't shift floats
            case LSHIFT:
            case LSHIFTX:
            case RSIGNEDSHIFT:
            case RSIGNEDSHIFTX:
            case RUNSIGNEDSHIFT:
            case RUNSIGNEDSHIFTX:
                throw new UtilEvalException("Can't shift floats ");

            default:
                throw new InterpreterException(
                        "Unimplemented binary float operator");
        }
    }

    /**
     * Promote primitive wrapper type to to Integer wrapper type
     */
    static Object promoteToInteger(Object wrapper) {
        if (wrapper instanceof Character)
            return (int) (Character) wrapper;
        else if ((wrapper instanceof Byte) || (wrapper instanceof Short))
            return ((Number) wrapper).intValue();

        return wrapper;
    }

    /**
     * Promote the pair of primitives to the maximum type of the two.
     * e.g. [int,long]->[long,long]
     */
    static Object[] promotePrimitives(Object lhs, Object rhs) {
        lhs = promoteToInteger(lhs);
        rhs = promoteToInteger(rhs);

        if ((lhs instanceof Number) && (rhs instanceof Number)) {
            Number lnum = (Number) lhs;
            Number rnum = (Number) rhs;

            boolean b;

            if ((b = (lnum instanceof Double)) || (rnum instanceof Double)) {
                if (b)
                    rhs = rnum.doubleValue();
                else
                    lhs = lnum.doubleValue();
            } else if ((b = (lnum instanceof Float)) || (rnum instanceof Float)) {
                if (b)
                    rhs = rnum.floatValue();
                else
                    lhs = lnum.floatValue();
            } else if ((b = (lnum instanceof Long)) || (rnum instanceof Long)) {
                if (b)
                    rhs = rnum.longValue();
                else
                    lhs = lnum.longValue();
            }
        }

        return new Object[]{lhs, rhs};
    }

    public static Primitive unaryOperation(Primitive val, int kind)
            throws UtilEvalException {
        if (val == NULL)
            throw new UtilEvalException(
                    "illegal use of null object or 'null' literal");
        if (val == VOID)
            throw new UtilEvalException(
                    "illegal use of undefined object or 'void' literal");

        Class operandType = val.getType();
        Object operand = promoteToInteger(val.getValue());

        if (operand instanceof Boolean)
            return new Primitive(booleanUnaryOperation((Boolean) operand, kind));
        else if (operand instanceof Integer) {
            int result = intUnaryOperation((Integer) operand, kind);

            // ++ and -- must be cast back the original type
            if (kind == INCR || kind == DECR) {
                if (operandType == Byte.TYPE)
                    return new Primitive((byte) result);
                if (operandType == Short.TYPE)
                    return new Primitive((short) result);
                if (operandType == Character.TYPE)
                    return new Primitive((char) result);
            }

            return new Primitive(result);
        } else if (operand instanceof Long)
            return new Primitive(longUnaryOperation((Long) operand, kind));
        else if (operand instanceof Float)
            return new Primitive(floatUnaryOperation((Float) operand, kind));
        else if (operand instanceof Double)
            return new Primitive(doubleUnaryOperation((Double) operand, kind));
        else
            throw new InterpreterException(
                    "An error occurred.  Please call technical support.");
    }

    static boolean booleanUnaryOperation(Boolean B, int kind)
            throws UtilEvalException {
        boolean operand = B;
        switch (kind) {
            case BANG:
                return !operand;
            default:
                throw new UtilEvalException("Operator inappropriate for boolean");
        }
    }

    static int intUnaryOperation(Integer I, int kind) {
        int operand = I;

        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            case TILDE:
                return ~operand;
            case INCR:
                return operand + 1;
            case DECR:
                return operand - 1;
            default:
                throw new InterpreterException("bad integer unaryOperation");
        }
    }

    static long longUnaryOperation(Long L, int kind) {
        long operand = L;

        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            case TILDE:
                return ~operand;
            case INCR:
                return operand + 1;
            case DECR:
                return operand - 1;
            default:
                throw new InterpreterException("bad long unaryOperation");
        }
    }

    static float floatUnaryOperation(Float F, int kind) {
        float operand = F;

        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            default:
                throw new InterpreterException("bad float unaryOperation");
        }
    }

    static double doubleUnaryOperation(Double D, int kind) {
        double operand = D;

        switch (kind) {
            case PLUS:
                return operand;
            case MINUS:
                return -operand;
            default:
                throw new InterpreterException("bad double unaryOperation");
        }
    }

    public int intValue() throws UtilEvalException {
        if (value instanceof Number)
            return ((Number) value).intValue();
        else
            throw new UtilEvalException("Primitive not a number");
    }

    public boolean booleanValue() throws UtilEvalException {
        if (value instanceof Boolean)
            return (Boolean) value;
        else
            throw new UtilEvalException("Primitive not a boolean");
    }

    /**
     * Determine if this primitive is a numeric type.
     * i.e. not boolean, null, or void (but including char)
     */
    public boolean isNumber() {
        return (!(value instanceof Boolean)
                && !(this == NULL) && !(this == VOID));
    }

    public Number numberValue() throws UtilEvalException {
        Object value = this.value;

        // Promote character to Number type for these purposes
        if (value instanceof Character)
            value = (int) (Character) value;

        if (value instanceof Number)
            return (Number) value;
        else
            throw new UtilEvalException("Primitive not a number");
    }

    /**
     * Primitives compare equal with other Primitives containing an equal
     * wrapped value.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Primitive)
            return ((Primitive) obj).value.equals(this.value);
        else
            return false;
    }

    /**
     * The hash of the Primitive is tied to the hash of the wrapped value but
     * shifted so that they are not the same.
     */
    public int hashCode() {
        return this.value.hashCode() * 21; // arbitrary
    }

    /**
     * Unwrap primitive values and map voids to nulls.
     * Non Primitive types remain unchanged.
     *
     * @param obj object type which may be krine.Primitive
     * @return corresponding "normal" Java type, "unwrapping"
     * any krine.Primitive types to their wrapper types.
     */
    public static Object unwrap(Object obj) {
        // map voids to nulls for the outside world
        if (obj == Primitive.VOID)
            return null;

        // unwrap primitives
        if (obj instanceof Primitive)
            return ((Primitive) obj).getValue();
        else
            return obj;
    }

    /*
        Unwrap Primitive wrappers to their java.lang wrapper values.
		e.g. Primitive(42) becomes Integer(42)
		@see #unwrap( Object )
    */
    public static Object[] unwrap(Object[] args) {
        Object[] oa = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            oa[i] = unwrap(args[i]);
        return oa;
    }

    /*
    */
    public static Object[] wrap(Object[] args, Class[] paramTypes) {
        if (args == null)
            return null;

        Object[] oa = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            oa[i] = wrap(args[i], paramTypes[i]);
        return oa;
    }

    /**
     * Wrap primitive values (as indicated by type param) and nulls in the
     * Primitive class.  Values not primitive or null are left unchanged.
     * Primitive values are represented by their wrapped values in param value.
     * <p/>
     * The value null is mapped to Primitive.NULL.
     * Any value specified with type Void.TYPE is mapped to Primitive.VOID.
     */
    public static Object wrap(
            Object value, Class type) {
        if (type == Void.TYPE)
            return Primitive.VOID;

        if (value == null)
            return Primitive.NULL;

        if (type.isPrimitive())
            return new Primitive(value);

        return value;
    }


    /**
     * Get the appropriate default value per JLS 4.5.4
     */
    public static Primitive getDefaultValue(Class type) {
        if (type == null || !type.isPrimitive())
            return Primitive.NULL;
        if (type == Boolean.TYPE)
            return new Primitive(false);

        // non boolean primitive, get appropriate flavor of zero
        try {
            return new Primitive(0).castToType(type, Types.CAST);
        } catch (UtilEvalException e) {
            throw new InterpreterException("bad cast");
        }
    }

    /**
     * Get the corresponding java.lang wrapper class for the primitive TYPE
     * class.
     * e.g.  Integer.TYPE -> Integer.class
     */
    public static Class boxType(Class primitiveType) {
        Class c = wrapperMap.get(primitiveType);
        if (c != null)
            return c;
        throw new InterpreterException(
                "Not a primitive type: " + primitiveType);
    }

    /**
     * Get the corresponding primitive TYPE class for the java.lang wrapper
     * class type.
     * e.g.  Integer.class -> Integer.TYPE
     */
    public static Class unboxType(Class wrapperType) {
        Class c = wrapperMap.get(wrapperType);
        if (c != null)
            return c;
        throw new InterpreterException(
                "Not a primitive wrapper type: " + wrapperType);
    }

    /**
     * Cast this krine.Primitive value to a new krine.Primitive value
     * This is usually a numeric type cast.  Other cases include:
     * A boolean can be cast to boolen
     * null can be cast to any object type and remains null
     * Attempting to cast a void causes an exception
     *
     * @param toType is the java object or primitive TYPE class
     */
    public Primitive castToType(Class toType, int operation)
            throws UtilEvalException {
        return castPrimitive(
                toType, getType()/*fromType*/, this/*fromValue*/,
                false/*checkOnly*/, operation);
    }

    /*
        Cast or check a cast of a primitive type to another type.
        Normally both types are primitive (e.g. numeric), but a null value
        (no type) may be cast to any type.
        <p/>

        @param toType is the target type of the cast.  It is normally a
        java primitive TYPE, but in the case of a null cast can be any object
        type.

        @param fromType is the java primitive TYPE type of the primitive to be
        cast or null, to indicate that the fromValue was null or void.

        @param fromValue is, optionally, the value to be converted.  If
        checkOnly is true fromValue must be null.  If checkOnly is false,
        fromValue must be non-null (Primitive.NULL is of course valid).
    */
    static Primitive castPrimitive(
            Class toType, Class fromType, Primitive fromValue,
            boolean checkOnly, int operation)
            throws UtilEvalException {
        /*
            Lots of preconditions checked here...
			Once things are running smoothly we might comment these out
			(That's what assertions are for).
		*/
        if (checkOnly && fromValue != null)
            throw new InterpreterException("bad cast param 1");
        if (!checkOnly && fromValue == null)
            throw new InterpreterException("bad cast param 2");
        if (fromType != null && !fromType.isPrimitive())
            throw new InterpreterException("bad fromType:" + fromType);
        if (fromValue == Primitive.NULL && fromType != null)
            throw new InterpreterException("inconsistent args 1");
        if (fromValue == Primitive.VOID && fromType != Void.TYPE)
            throw new InterpreterException("inconsistent args 2");

        // can't cast void to anything
        if (fromType == Void.TYPE)
            if (checkOnly)
                return Types.INVALID_CAST;
            else
                throw Types.castError(Reflect.normalizeClassName(toType),
                        "void value", operation);

        // unwrap Primitive fromValue to its wrapper value, etc.
        Object value = null;
        if (fromValue != null)
            value = fromValue.getValue();

        if (toType.isPrimitive()) {
            // Trying to cast null to primitive type?
            if (fromType == null)
                if (checkOnly)
                    return Types.INVALID_CAST;
                else
                    throw Types.castError(
                            "primitive type:" + toType, "Null value", operation);

            // fall through
        } else {
            // Trying to cast primitive to an object type
            // Primitive.NULL can be cast to any object type
            if (fromType == null)
                return checkOnly ? Types.VALID_CAST :
                        Primitive.NULL;

            if (checkOnly)
                return Types.INVALID_CAST;
            else
                throw Types.castError(
                        "object type:" + toType, "primitive value", operation);
        }

        // can only cast boolean to boolean
        if (fromType == Boolean.TYPE) {
            if (toType != Boolean.TYPE)
                if (checkOnly)
                    return Types.INVALID_CAST;
                else
                    throw Types.castError(toType, fromType, operation);

            return checkOnly ? Types.VALID_CAST :
                    fromValue;
        }

        // Do numeric cast

        // Only allow legal Java assignment unless we're a CAST operation
        if (operation == Types.ASSIGNMENT
                && !Types.isJavaAssignable(toType, fromType)
                ) {
            if (checkOnly)
                return Types.INVALID_CAST;
            else
                throw Types.castError(toType, fromType, operation);
        }

        return checkOnly ? Types.VALID_CAST :
                new Primitive(castWrapper(toType, value));
    }

    public static boolean isWrapperType(Class type) {
        return wrapperMap.get(type) != null && !type.isPrimitive();
    }

    /**
     * Cast a primitive value represented by its java.lang wrapper type to the
     * specified java.lang wrapper type.  e.g.  Byte(5) to Integer(5) or
     * Integer(5) to Byte(5)
     *
     * @param toType is the java TYPE type
     * @param value  is the value in java.lang wrapper.
     *               value may not be null.
     */
    static Object castWrapper(
            Class toType, Object value) {
        if (!toType.isPrimitive())
            throw new InterpreterException("invalid type in castWrapper: " + toType);
        if (value == null)
            throw new InterpreterException("null value in castWrapper, guard");
        if (value instanceof Boolean) {
            if (toType != Boolean.TYPE)
                throw new InterpreterException("bad wrapper cast of boolean");
            else
                return value;
        }

        // first promote char to Number type to avoid duplicating code
        if (value instanceof Character)
            value = (int) (Character) value;

        if (!(value instanceof Number))
            throw new InterpreterException("bad type in cast");

        Number number = (Number) value;

        if (toType == Byte.TYPE)
            return number.byteValue();
        if (toType == Short.TYPE)
            return number.shortValue();
        if (toType == Character.TYPE)
            return (char) number.intValue();
        if (toType == Integer.TYPE)
            return number.intValue();
        if (toType == Long.TYPE)
            return number.longValue();
        if (toType == Float.TYPE)
            return number.floatValue();
        if (toType == Double.TYPE)
            return number.doubleValue();

        throw new InterpreterException("error in wrapper cast");
    }

}
