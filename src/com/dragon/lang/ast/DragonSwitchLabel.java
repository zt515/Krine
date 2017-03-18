package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonSwitchLabel extends SimpleNode {
    boolean isDefault;

    public DragonSwitchLabel(int id) {
        super(id);
    }

    public Object eval(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        waitForDebugger();

        if (isDefault)
            return null; // should probably error
        SimpleNode label = ((SimpleNode) jjtGetChild(0));
        return label.eval(callstack, dragonBasicInterpreter);
    }
}
