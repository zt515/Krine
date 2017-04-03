package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineStatementExpressionList extends SimpleNode {
    KrineStatementExpressionList(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        int n = jjtGetNumChildren();
        for (int i = 0; i < n; i++) {
            SimpleNode node = ((SimpleNode) jjtGetChild(i));
            node.eval(callStack, krineBasicInterpreter);
        }
        return Primitive.VOID;
    }
}

