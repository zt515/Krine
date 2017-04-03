package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

class KrineUnaryExpression extends SimpleNode implements ParserConstants {
    public int kind;
    public boolean postfix = false;

    KrineUnaryExpression(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        SimpleNode node = (SimpleNode) jjtGetChild(0);

        // If this is a unary increment of decrement (either pre or postfix)
        // then we need an LeftValue to which to assign the result.  Otherwise
        // just do the unary operation for the value.
        try {
            if (kind == INCR || kind == DECR) {
                LeftValue lhs = ((KrinePrimaryExpression) node).toLHS(
                        callStack, krineBasicInterpreter);
                return lhsUnaryOperation(lhs, krineBasicInterpreter.isStrictJava());
            } else
                return
                        unaryOperation(node.eval(callStack, krineBasicInterpreter), kind);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
    }

    private Object lhsUnaryOperation(LeftValue lhs, boolean strictJava)
            throws UtilEvalException {
        if (KrineBasicInterpreter.DEBUG) KrineBasicInterpreter.debug("lhsUnaryOperation");
        Object preValue, postValue;
        preValue = lhs.getValue();
        postValue = unaryOperation(preValue, kind);

        Object retVal;
        if (postfix)
            retVal = preValue;
        else
            retVal = postValue;

        lhs.assign(postValue, strictJava);
        return retVal;
    }

    private Object unaryOperation(Object op, int kind) throws UtilEvalException {
        if (op instanceof Boolean || op instanceof Character
                || op instanceof Number)
            return primitiveWrapperUnaryOperation(op, kind);

        if (!(op instanceof Primitive))
            throw new UtilEvalException("Unary operation " + tokenImage[kind]
                    + " inappropriate for object");


        return Primitive.unaryOperation((Primitive) op, kind);
    }

    private Object primitiveWrapperUnaryOperation(Object val, int kind)
            throws UtilEvalException {
        Class operandType = val.getClass();
        Object operand = Primitive.promoteToInteger(val);

        if (operand instanceof Boolean)
            return Primitive.booleanUnaryOperation((Boolean) operand, kind);
        else if (operand instanceof Integer) {
            int result = Primitive.intUnaryOperation((Integer) operand, kind);

            // ++ and -- must be cast back the original type
            if (kind == INCR || kind == DECR) {
                if (operandType == Byte.TYPE)
                    return (byte) result;
                if (operandType == Short.TYPE)
                    return (short) result;
                if (operandType == Character.TYPE)
                    return (char) result;
            }

            return result;
        } else if (operand instanceof Long)
            return Primitive.longUnaryOperation((Long) operand, kind);
        else if (operand instanceof Float)
            return Primitive.floatUnaryOperation((Float) operand, kind);
        else if (operand instanceof Double)
            return Primitive.doubleUnaryOperation((Double) operand, kind);
        else
            throw new InterpreterException("An error occurred.  Please call technical support.");
    }
}
