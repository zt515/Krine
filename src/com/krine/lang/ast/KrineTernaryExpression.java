package com.krine.lang.ast;

import com.krine.lang.utils.CallStack;
import com.krine.lang.KrineBasicInterpreter;

/**
 * TODO: This class needs logic to prevent the right hand side of boolean logical
 * expressions from being naively evaluated...  e.g. for "foo && bar" bar
 * should not be evaluated in the case where foo is true.
 */
class KrineTernaryExpression extends SimpleNode {

    KrineTernaryExpression(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        SimpleNode
                cond = (SimpleNode) jjtGetChild(0),
                evalTrue = (SimpleNode) jjtGetChild(1),
                evalFalse = (SimpleNode) jjtGetChild(2);

        if (KrineIfStatement.evaluateCondition(cond, callstack, krineBasicInterpreter))
            return evalTrue.eval(callstack, krineBasicInterpreter);
        else
            return evalFalse.eval(callstack, krineBasicInterpreter);
    }

}
