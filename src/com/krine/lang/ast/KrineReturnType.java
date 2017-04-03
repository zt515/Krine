package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineReturnType extends SimpleNode {
    public boolean isVoid;

    KrineReturnType(int id) {
        super(id);
    }

    KrineType getTypeNode() {
        return (KrineType) jjtGetChild(0);
    }

    public String getTypeDescriptor(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, String defaultPackage) {
        if (isVoid)
            return "V";
        else
            return getTypeNode().getTypeDescriptor(
                    callStack, krineBasicInterpreter, defaultPackage);
    }

    public Class evalReturnType(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        if (isVoid)
            return Void.TYPE;
        else
            return getTypeNode().getType(callStack, krineBasicInterpreter);
    }
}

