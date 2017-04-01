package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineStatementExpressionList extends SimpleNode {
    KrineStatementExpressionList(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        int n = jjtGetNumChildren();
        for (int i = 0; i < n; i++) {
            SimpleNode node = ((SimpleNode) jjtGetChild(i));
            node.eval(callstack, krineBasicInterpreter);
        }
        return Primitive.VOID;
    }
}

