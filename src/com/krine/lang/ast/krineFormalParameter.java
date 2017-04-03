package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

/**
 * A formal parameter declaration.
 * For loose variable declaration type is null.
 */
class krineFormalParameter extends SimpleNode {
    public static final Class UNTYPED = null;
    public String name;
    // unsafe caching of type here
    public Class type;

    krineFormalParameter(int id) {
        super(id);
    }

    public String getTypeDescriptor(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, String defaultPackage) {
        if (jjtGetNumChildren() > 0)
            return ((KrineType) jjtGetChild(0)).getTypeDescriptor(
                    callStack, krineBasicInterpreter, defaultPackage);
        else
            // this will probably not get used
            return "Ljava/lang/Object;";  // Object type
    }

    /**
     * Evaluate the type.
     */
    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        if (jjtGetNumChildren() > 0)
            type = ((KrineType) jjtGetChild(0)).getType(callStack, krineBasicInterpreter);
        else
            type = UNTYPED;

        return type;
    }
}

