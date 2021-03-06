package com.krine.lang;

import com.krine.lang.ast.EvalError;
import com.krine.lang.ast.SimpleNode;
import com.krine.lang.utils.CallStack;

/**
 * UtilEvalException is an error corresponding to an EvalError but thrown by a
 * utility or other class that does not have the caller context (Node)
 * available to it.  A normal EvalError must supply the caller Node in order
 * for error messages to be pinned to the correct line and location in the
 * script.  UtilEvalException is a checked exception that is *not* a subtype of
 * EvalError, but instead must be caught and rethrown as an EvalError by
 * the a nearest location with context.  The method toEvalError( Node )
 * should be used to throw the EvalError, supplying the node.
 * <p>
 * <p>
 * To summarize: Utilities throw UtilEvalException.  ASTs throw EvalError.
 * ASTs catch UtilEvalException and rethrow it as EvalError using
 * toEvalError( Node ).
 * <p>
 * <p>
 * Philosophically, EvalError and UtilEvalException correspond to
 * RuntimeException.  However they are constrained in this way in order to
 * add the context for error reporting.
 *
 * @see UtilTargetException
 */
public class UtilEvalException extends Exception {
    protected UtilEvalException() {
    }

    public UtilEvalException(String s) {
        super(s);
    }

    public UtilEvalException(String s, Throwable cause) {
        super(s, cause);
    }

    /**
     * Re-throw as an eval error, prefixing msg to the message and specifying
     * the node.  If a node already exists the addNode is ignored.
     *
     * @param msg may be null for no additional message.
     * @see com.krine.lang.ast.EvalError#setNode(com.krine.lang.ast.SimpleNode)
     * <p>
     */
    public EvalError toEvalError(
            String msg, SimpleNode node, CallStack callStack) {
        if (KrineBasicInterpreter.DEBUG)
            printStackTrace();

        if (msg == null)
            msg = "";
        else
            msg = msg + ": ";
        return new EvalError(msg + getMessage(), node, callStack, this);
    }

    public EvalError toEvalError(SimpleNode node, CallStack callStack) {
        return toEvalError(null, node, callStack);
    }

}

