package com.krine.lang;

import com.krine.lang.ast.KrineTargetException;
import com.krine.lang.ast.EvalError;
import com.krine.lang.ast.SimpleNode;
import com.krine.lang.utils.CallStack;

/**
 * UtilTargetException is an error corresponding to a KrineTargetException but thrown by a
 * utility or other class that does not have the caller context (Node)
 * available to it.  See UtilEvalException for an explanation of the difference
 * between UtilEvalException and EvalError.
 * <p>
 *
 * @see UtilEvalException
 */
public class UtilTargetException extends UtilEvalException {
    public Throwable t;

    public UtilTargetException(String message, Throwable t) {
        super(message);
        this.t = t;
    }

    public UtilTargetException(Throwable t) {
        this(null, t);
    }

    /**
     * Override toEvalError to throw KrineTargetException type.
     */
    public EvalError toEvalError(
            String msg, SimpleNode node, CallStack callstack) {
        if (msg == null)
            msg = getMessage();
        else
            msg = msg + ": " + getMessage();

        return new KrineTargetException(msg, t, node, callstack, false);
    }
}

