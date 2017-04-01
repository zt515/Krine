package com.krine.lang.ast;

import com.krine.lang.utils.CallStack;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;

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
            CallStack callstack, KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        waitForDebugger();

        NameSpace namespace = callstack.top();
        Class toType = ((KrineType) jjtGetChild(0)).getType(
                callstack, krineBasicInterpreter);
        SimpleNode expression = (SimpleNode) jjtGetChild(1);

        // evaluate the expression
        Object fromValue = expression.eval(callstack, krineBasicInterpreter);
        Class fromType = fromValue.getClass();

        // TODO: need to add isJavaCastable() test for strictJava
        // (as opposed to isJavaAssignable())
        try {
            return Types.castObject(fromValue, toType, Types.CAST);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

}
