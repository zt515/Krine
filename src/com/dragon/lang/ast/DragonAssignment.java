package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.InterpreterException;
import com.dragon.lang.UtilEvalException;

class DragonAssignment extends SimpleNode implements ParserConstants {
    public int operator;

    DragonAssignment(int id) {
        super(id);
    }

    public Object eval(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        DragonPrimaryExpression lhsNode =
                (DragonPrimaryExpression) jjtGetChild(0);

        if (lhsNode == null)
            throw new InterpreterException("Error, null LHSnode");

        boolean strictJava = dragonBasicInterpreter.getStrictJava();
        LeftValue lhs = lhsNode.toLHS(callstack, dragonBasicInterpreter);
        if (lhs == null)
            throw new InterpreterException("Error, null LeftValue");

        // For operator-assign operations save the lhs value before evaluating
        // the rhs.  This is correct Java behavior for postfix operations
        // e.g. i=1; i+=i++; // should be 2 not 3
        Object lhsValue = null;
        if (operator != ASSIGN) // assign doesn't need the pre-value
            try {
                lhsValue = lhs.getValue();
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callstack);
            }

        SimpleNode rhsNode = (SimpleNode) jjtGetChild(1);

        Object rhs;

        // implement "blocks" foo = { };
        // if ( rhsNode instanceof DragonBlock )
        //    rsh =
        // else
        rhs = rhsNode.eval(callstack, dragonBasicInterpreter);

        if (rhs == Primitive.VOID)
            throw new EvalError("Void assignment.", this, callstack);

        try {
            switch (operator) {
                case ASSIGN:
                    return lhs.assign(rhs, strictJava);

                case PLUSASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, PLUS), strictJava);

                case MINUSASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, MINUS), strictJava);

                case STARASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, STAR), strictJava);

                case SLASHASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, SLASH), strictJava);

                case ANDASSIGN:
                case ANDASSIGNX:
                    return lhs.assign(
                            operation(lhsValue, rhs, BIT_AND), strictJava);

                case ORASSIGN:
                case ORASSIGNX:
                    return lhs.assign(
                            operation(lhsValue, rhs, BIT_OR), strictJava);

                case XORASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, XOR), strictJava);

                case MODASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, MOD), strictJava);

                case LSHIFTASSIGN:
                case LSHIFTASSIGNX:
                    return lhs.assign(
                            operation(lhsValue, rhs, LSHIFT), strictJava);

                case RSIGNEDSHIFTASSIGN:
                case RSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                            operation(lhsValue, rhs, RSIGNEDSHIFT), strictJava);

                case RUNSIGNEDSHIFTASSIGN:
                case RUNSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                            operation(lhsValue, rhs, RUNSIGNEDSHIFT),
                            strictJava);

                default:
                    throw new InterpreterException(
                            "unimplemented operator in assignment Dragon");
            }
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

    private Object operation(Object lhs, Object rhs, int kind)
            throws UtilEvalException {
        /*
            Implement String += value;
			According to the JLS, value may be anything.
			In Dragon, we'll disallow VOID (undefined) values.
			(or should we map them to the empty string?)
		*/
        if (lhs instanceof String && rhs != Primitive.VOID) {
            if (kind != PLUS)
                throw new UtilEvalException(
                        "Use of non + operator with String LeftValue");

            return (String) lhs + rhs;
        }

        if (lhs instanceof Primitive || rhs instanceof Primitive)
            if (lhs == Primitive.VOID || rhs == Primitive.VOID)
                throw new UtilEvalException(
                        "Illegal use of undefined object or 'void' literal");
            else if (lhs == Primitive.NULL || rhs == Primitive.NULL)
                throw new UtilEvalException(
                        "Illegal use of null object or 'null' literal");


        if ((lhs instanceof Boolean || lhs instanceof Character ||
                lhs instanceof Number || lhs instanceof Primitive) &&
                (rhs instanceof Boolean || rhs instanceof Character ||
                        rhs instanceof Number || rhs instanceof Primitive)) {
            return Primitive.binaryOperation(lhs, rhs, kind);
        }

        throw new UtilEvalException("Non primitive value in operator: " +
                lhs.getClass() + " " + tokenImage[kind] + " " + rhs.getClass());
    }
}
