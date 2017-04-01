package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineThrowStatement extends SimpleNode {
    KrineThrowStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Object obj = ((SimpleNode) jjtGetChild(0)).eval(callstack, krineBasicInterpreter);

        // need to loosen this to any throwable... do we need to handle
        // that in krineBasicInterpreter somewhere?  check first...
        if (!(obj instanceof Exception))
            throw new EvalError("Expression in 'throw' must be Exception type",
                    this, callstack);

        // wrap the exception in a TargetException to propogate it up
        throw new KrineTargetException((Exception) obj, this, callstack);
    }
}

