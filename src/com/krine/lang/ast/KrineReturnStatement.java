package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineReturnStatement extends SimpleNode implements ParserConstants {
    public int kind;

    KrineReturnStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Object value;
        if (jjtGetNumChildren() > 0)
            value = ((SimpleNode) jjtGetChild(0)).eval(callStack, krineBasicInterpreter);
        else
            value = Primitive.VOID;

        return new ReturnControl(kind, value, this);
    }
}

