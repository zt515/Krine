package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineSwitchLabel extends SimpleNode {
    boolean isDefault;

    public KrineSwitchLabel(int id) {
        super(id);
    }

    public Object eval(
            CallStack callstack, KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        waitForDebugger();

        if (isDefault)
            return null; // should probably error
        SimpleNode label = ((SimpleNode) jjtGetChild(0));
        return label.eval(callstack, krineBasicInterpreter);
    }
}
