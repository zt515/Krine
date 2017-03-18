package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;

/**
 * A formal parameter declaration.
 * For loose variable declaration type is null.
 */
class DragonFormalParameter extends SimpleNode {
    public static final Class UNTYPED = null;
    public String name;
    // unsafe caching of type here
    public Class type;

    DragonFormalParameter(int id) {
        super(id);
    }

    public String getTypeDescriptor(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        if (jjtGetNumChildren() > 0)
            return ((DragonType) jjtGetChild(0)).getTypeDescriptor(
                    callstack, dragonBasicInterpreter, defaultPackage);
        else
            // this will probably not get used
            return "Ljava/lang/Object;";  // Object type
    }

    /**
     * Evaluate the type.
     */
    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        if (jjtGetNumChildren() > 0)
            type = ((DragonType) jjtGetChild(0)).getType(callstack, dragonBasicInterpreter);
        else
            type = UNTYPED;

        return type;
    }
}

