package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;

class DragonReturnStatement extends SimpleNode implements ParserConstants {
    public int kind;

    DragonReturnStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Object value;
        if (jjtGetNumChildren() > 0)
            value = ((SimpleNode) jjtGetChild(0)).eval(callstack, dragonBasicInterpreter);
        else
            value = Primitive.VOID;

        return new ReturnControl(kind, value, this);
    }
}

