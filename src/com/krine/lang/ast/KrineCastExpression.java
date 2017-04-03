package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

/**
 * Implement casts.
 * <p>
 * I think it should be possible to simplify some of the code here by
 * using the Types.getAssignableForm() method, but I haven't looked
 * into it.
 */
class KrineCastExpression extends SimpleNode {

    public KrineCastExpression(int id) {
        super(id);
    }

    /**
     * @return the result of the cast.
     */
    public Object eval(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        waitForDebugger();

        NameSpace namespace = callStack.top();
        Class toType = ((KrineType) jjtGetChild(0)).getType(
                callStack, krineBasicInterpreter);
        SimpleNode expression = (SimpleNode) jjtGetChild(1);

        // evaluate the expression
        Object fromValue = expression.eval(callStack, krineBasicInterpreter);
        Class fromType = fromValue.getClass();

        // TODO: need to add isJavaCastable() test for strictJava
        // (as opposed to isJavaAssignable())
        try {
            return Types.castObject(fromValue, toType, Types.CAST);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
    }

}
