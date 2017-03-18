package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.UtilEvalException;

import java.lang.reflect.Array;

class DragonArrayInitializer extends SimpleNode {
    DragonArrayInitializer(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        throw new EvalError("Array initializer has no base type.",
                this, callstack);
    }

    /**
     * Construct the array from the initializer syntax.
     *
     * @param baseType   the base class type of the array (no dimensionality)
     * @param dimensions the top number of dimensions of the array
     *                   e.g. 2 for a String [][];
     */
    public Object eval(Class baseType, int dimensions,
                       CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        int numInitializers = jjtGetNumChildren();

        // allocate the array to store the initializers
        int[] dima = new int[dimensions]; // description of the array
        // The other dimensions default to zero and are assigned when
        // the values are set.
        dima[0] = numInitializers;
        Object initializers = Array.newInstance(baseType, dima);

        // Evaluate the initializers
        for (int i = 0; i < numInitializers; i++) {
            SimpleNode node = (SimpleNode) jjtGetChild(i);
            Object currentInitializer;
            if (node instanceof DragonArrayInitializer) {
                if (dimensions < 2)
                    throw new EvalError(
                            "Invalid Location for Intializer, position: " + i,
                            this, callstack);
                currentInitializer =
                        ((DragonArrayInitializer) node).eval(
                                baseType, dimensions - 1, callstack, dragonBasicInterpreter);
            } else
                currentInitializer = node.eval(callstack, dragonBasicInterpreter);

            if (currentInitializer == Primitive.VOID)
                throw new EvalError(
                        "Void in array initializer, position" + i, this, callstack);

            // Determine if any conversion is necessary on the initializers.
            //
            // Quick test to see if conversions apply:
            // If the dimensionality of the array is 1 then the elements of
            // the initializer can be primitives or boxable types.  If it is
            // greater then the values must be array (object) types and there
            // are currently no conversions that we do on those.
            // If we have conversions on those in the future then we need to
            // get the real base type here instead of the dimensionless one.
            Object value = currentInitializer;
            if (dimensions == 1) {
                // We do a dragon cast here.  strictJava should be able to affect
                // the cast there when we tighten control
                try {
                    value = Types.castObject(
                            currentInitializer, baseType, Types.CAST);
                } catch (UtilEvalException e) {
                    throw e.toEvalError(
                            "Error in array initializer", this, callstack);
                }
                // unwrap any primitive, map voids to null, etc.
                value = Primitive.unwrap(value);
            }

            // store the value in the array
            try {
                Array.set(initializers, i, value);
            } catch (IllegalArgumentException e) {
                DragonBasicInterpreter.debug("illegal arg" + e);
                throwTypeError(baseType, currentInitializer, i, callstack);
            } catch (ArrayStoreException e) { // I think this can happen
                DragonBasicInterpreter.debug("arraystore" + e);
                throwTypeError(baseType, currentInitializer, i, callstack);
            }
        }

        return initializers;
    }

    private void throwTypeError(
            Class baseType, Object initializer, int argNum, CallStack callstack)
            throws EvalError {
        String rhsType;
        if (initializer instanceof Primitive)
            rhsType =
                    ((Primitive) initializer).getType().getName();
        else
            rhsType = Reflect.normalizeClassName(
                    initializer.getClass());

        throw new EvalError("Incompatible type: " + rhsType
                + " in initializer of array type: " + baseType
                + " at position: " + argNum, this, callstack);
    }

}
