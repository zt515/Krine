package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

/**
 * TODO: This class needs logic to prevent the right hand side of boolean logical
 * expressions from being naively evaluated...  e.g. for "foo && bar" bar
 * should not be evaluated in the case where foo is true.
 */
class KrineTernaryExpression extends SimpleNode {

    KrineTernaryExpression(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        SimpleNode
                cond = (SimpleNode) jjtGetChild(0),
                evalTrue = (SimpleNode) jjtGetChild(1),
                evalFalse = (SimpleNode) jjtGetChild(2);

        if (KrineIfStatement.evaluateCondition(cond, callStack, krineBasicInterpreter))
            return evalTrue.eval(callStack, krineBasicInterpreter);
        else
            return evalFalse.eval(callStack, krineBasicInterpreter);
    }

}
