package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.UtilEvalException;

/**
 * Implement casts.
 * <p>
 * I think it should be possible to simplify some of the code here by
 * using the Types.getAssignableForm() method, but I haven't looked
 * into it.
 */
class DragonCastExpression extends SimpleNode {

    public DragonCastExpression(int id) {
        super(id);
    }

    /**
     * @return the result of the cast.
     */
    public Object eval(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        waitForDebugger();

        NameSpace namespace = callstack.top();
        Class toType = ((DragonType) jjtGetChild(0)).getType(
                callstack, dragonBasicInterpreter);
        SimpleNode expression = (SimpleNode) jjtGetChild(1);

        // evaluate the expression
        Object fromValue = expression.eval(callstack, dragonBasicInterpreter);
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
