package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineIfStatement extends SimpleNode {
    KrineIfStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        Object ret = null;

        waitForDebugger();

        if (evaluateCondition(
                (SimpleNode) jjtGetChild(0), callStack, krineBasicInterpreter))
            ret = ((SimpleNode) jjtGetChild(1)).eval(callStack, krineBasicInterpreter);
        else if (jjtGetNumChildren() > 2)
            ret = ((SimpleNode) jjtGetChild(2)).eval(callStack, krineBasicInterpreter);

        if (ret instanceof ReturnControl)
            return ret;
        else
            return Primitive.VOID;
    }

    public static boolean evaluateCondition(
            SimpleNode condExp, CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        Object obj = condExp.eval(callStack, krineBasicInterpreter);
        if (obj instanceof Primitive) {
            if (obj == Primitive.VOID)
                throw new EvalError("Condition evaluates to void type",
                        condExp, callStack);
            obj = ((Primitive) obj).getValue();
        }

        if (obj instanceof Boolean)
            return (Boolean) obj;
        else
            throw new EvalError(
                    "Condition must evaluate to a Boolean or boolean.",
                    condExp, callStack);
    }
}
