package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;
import com.krine.lang.UtilEvalException;

class KrinePrimaryExpression extends SimpleNode {
    KrinePrimaryExpression(int id) {
        super(id);
    }

    /**
     * Evaluate to a value object.
     */
    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        return eval(false, callstack, krineBasicInterpreter);
    }

    /**
     * Evaluate to a value object.
     */
    public LeftValue toLHS(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        Object obj = eval(true, callstack, krineBasicInterpreter);

        if (!(obj instanceof LeftValue))
            throw new EvalError("Can't assign to:", this, callstack);
        else
            return (LeftValue) obj;
    }

    /*
        Our children are a prefix expression and any number of suffixes.
        <p>

        We don't eval() any nodes until the suffixes have had an
        opportunity to work through them.  This lets the suffixes decide
        how to interpret an ambiguous name (e.g. for the .class operation).
    */
    private Object eval(boolean toLHS,
                        CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Object obj = jjtGetChild(0);
        int numChildren = jjtGetNumChildren();

        for (int i = 1; i < numChildren; i++)
            obj = ((KrinePrimarySuffix) jjtGetChild(i)).doSuffix(
                    obj, toLHS, callstack, krineBasicInterpreter);

		/*
            If the result is a Node eval() it to an object or LeftValue
			(as determined by toLeftValue)
		*/
        if (obj instanceof SimpleNode)
            if (obj instanceof KrineAmbiguousName)
                if (toLHS)
                    obj = ((KrineAmbiguousName) obj).toLHS(
                            callstack, krineBasicInterpreter);
                else
                    obj = ((KrineAmbiguousName) obj).toObject(
                            callstack, krineBasicInterpreter);
            else
                // Some arbitrary kind of node
                if (toLHS)
                    // is this right?
                    throw new EvalError("Can't assign to prefix.",
                            this, callstack);
                else
                    obj = ((SimpleNode) obj).eval(callstack, krineBasicInterpreter);

        // return LeftValue or value object as determined by toLeftValue
        if (obj instanceof LeftValue)
            if (toLHS)
                return obj;
            else
                try {
                    return ((LeftValue) obj).getValue();
                } catch (UtilEvalException e) {
                    throw e.toEvalError(this, callstack);
                }
        else
            return obj;
    }
}
