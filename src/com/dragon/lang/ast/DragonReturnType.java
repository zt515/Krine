package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonReturnType extends SimpleNode {
    public boolean isVoid;

    DragonReturnType(int id) {
        super(id);
    }

    DragonType getTypeNode() {
        return (DragonType) jjtGetChild(0);
    }

    public String getTypeDescriptor(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        if (isVoid)
            return "V";
        else
            return getTypeNode().getTypeDescriptor(
                    callstack, dragonBasicInterpreter, defaultPackage);
    }

    public Class evalReturnType(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        if (isVoid)
            return Void.TYPE;
        else
            return getTypeNode().getType(callstack, dragonBasicInterpreter);
    }
}

