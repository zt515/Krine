package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;

/**
 * TODO: This class needs logic to prevent the right hand side of boolean logical
 * expressions from being naively evaluated...  e.g. for "foo && bar" bar
 * should not be evaluated in the case where foo is true.
 */
class DragonTernaryExpression extends SimpleNode {

    DragonTernaryExpression(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        SimpleNode
                cond = (SimpleNode) jjtGetChild(0),
                evalTrue = (SimpleNode) jjtGetChild(1),
                evalFalse = (SimpleNode) jjtGetChild(2);

        if (DragonIfStatement.evaluateCondition(cond, callstack, dragonBasicInterpreter))
            return evalTrue.eval(callstack, dragonBasicInterpreter);
        else
            return evalFalse.eval(callstack, dragonBasicInterpreter);
    }

}
