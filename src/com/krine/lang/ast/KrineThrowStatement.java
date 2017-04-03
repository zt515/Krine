package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineThrowStatement extends SimpleNode {
    KrineThrowStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Object obj = ((SimpleNode) jjtGetChild(0)).eval(callStack, krineBasicInterpreter);

        // need to loosen this to any throwable... do we need to handle
        // that in krineBasicInterpreter somewhere?  check first...
        if (!(obj instanceof Exception))
            throw new EvalError("Expression in 'throw' must be Exception type",
                    this, callStack);

        // wrap the exception in a TargetException to propagate it up
        throw new KrineTargetException((Exception) obj, this, callStack);
    }
}

