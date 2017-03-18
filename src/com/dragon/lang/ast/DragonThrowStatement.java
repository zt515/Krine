package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonThrowStatement extends SimpleNode {
    DragonThrowStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Object obj = ((SimpleNode) jjtGetChild(0)).eval(callstack, dragonBasicInterpreter);

        // need to loosen this to any throwable... do we need to handle
        // that in dragonBasicInterpreter somewhere?  check first...
        if (!(obj instanceof Exception))
            throw new EvalError("Expression in 'throw' must be Exception type",
                    this, callstack);

        // wrap the exception in a TargetException to propogate it up
        throw new DragonTargetException((Exception) obj, this, callstack);
    }
}

