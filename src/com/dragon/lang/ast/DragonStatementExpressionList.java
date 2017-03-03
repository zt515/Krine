package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonStatementExpressionList extends SimpleNode {
    DragonStatementExpressionList(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        int n = jjtGetNumChildren();
        for (int i = 0; i < n; i++) {
            SimpleNode node = ((SimpleNode) jjtGetChild(i));
            node.eval(callstack, dragonBasicInterpreter);
        }
        return Primitive.VOID;
    }
}

