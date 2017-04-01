package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

/**
 * This class handles both {@code while} statements and {@code do..while} statements.
 */
class KrineWhileStatement extends SimpleNode implements ParserConstants {

    /**
     * Set by Parser, default {@code false}
     */
    boolean isDoStatement;

    KrineWhileStatement(int id) {
        super(id);
    }


    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        waitForDebugger();

        int numChild = jjtGetNumChildren();

        // Order of body and condition is swapped for do / while
        final SimpleNode condExp;
        final SimpleNode body;

        if (isDoStatement) {
            condExp = (SimpleNode) jjtGetChild(1);
            body = (SimpleNode) jjtGetChild(0);
        } else {
            condExp = (SimpleNode) jjtGetChild(0);
            if (numChild > 1) {
                body = (SimpleNode) jjtGetChild(1);
            } else {
                body = null;
            }
        }

        boolean doOnceFlag = isDoStatement;

        while (doOnceFlag || KrineIfStatement.evaluateCondition(condExp, callstack, krineBasicInterpreter)) {
            doOnceFlag = false;
            // no body?
            if (body == null) {
                continue;
            }
            Object ret = body.eval(callstack, krineBasicInterpreter);
            if (ret instanceof ReturnControl) {
                switch (((ReturnControl) ret).kind) {
                    case RETURN:
                        return ret;

                    case CONTINUE:
                        break;

                    case BREAK:
                        return Primitive.VOID;
                }
            }
        }
        return Primitive.VOID;
    }

}
