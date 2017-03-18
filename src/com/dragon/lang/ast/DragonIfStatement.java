package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonIfStatement extends SimpleNode {
    DragonIfStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        Object ret = null;

        waitForDebugger();

        if (evaluateCondition(
                (SimpleNode) jjtGetChild(0), callstack, dragonBasicInterpreter))
            ret = ((SimpleNode) jjtGetChild(1)).eval(callstack, dragonBasicInterpreter);
        else if (jjtGetNumChildren() > 2)
            ret = ((SimpleNode) jjtGetChild(2)).eval(callstack, dragonBasicInterpreter);

        if (ret instanceof ReturnControl)
            return ret;
        else
            return Primitive.VOID;
    }

    public static boolean evaluateCondition(
            SimpleNode condExp, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        Object obj = condExp.eval(callstack, dragonBasicInterpreter);
        if (obj instanceof Primitive) {
            if (obj == Primitive.VOID)
                throw new EvalError("Condition evaluates to void type",
                        condExp, callstack);
            obj = ((Primitive) obj).getValue();
        }

        if (obj instanceof Boolean)
            return ((Boolean) obj).booleanValue();
        else
            throw new EvalError(
                    "Condition must evaluate to a Boolean or boolean.",
                    condExp, callstack);
    }
}
