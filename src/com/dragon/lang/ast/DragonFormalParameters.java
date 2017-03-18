package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;

class DragonFormalParameters extends SimpleNode {
    private String[] paramNames;
    /**
     * For loose type parameters the paramTypes are null.
     */
    // unsafe caching of types
    Class[] paramTypes;
    int numArgs;
    String[] typeDescriptors;

    DragonFormalParameters(int id) {
        super(id);
    }

    void insureParsed() {
        if (paramNames != null)
            return;

        this.numArgs = jjtGetNumChildren();
        String[] paramNames = new String[numArgs];

        for (int i = 0; i < numArgs; i++) {
            DragonFormalParameter param = (DragonFormalParameter) jjtGetChild(i);
            paramNames[i] = param.name;
        }

        this.paramNames = paramNames;
    }

    public String[] getParamNames() {
        insureParsed();
        return paramNames;
    }

    public String[] getTypeDescriptors(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        if (typeDescriptors != null)
            return typeDescriptors;

        insureParsed();
        String[] typeDesc = new String[numArgs];

        for (int i = 0; i < numArgs; i++) {
            DragonFormalParameter param = (DragonFormalParameter) jjtGetChild(i);
            typeDesc[i] = param.getTypeDescriptor(
                    callstack, dragonBasicInterpreter, defaultPackage);
        }

        this.typeDescriptors = typeDesc;
        return typeDesc;
    }

    /**
     * Evaluate the types.
     * Note that type resolution does not require the dragonBasicInterpreter instance.
     */
    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        if (paramTypes != null)
            return paramTypes;

        insureParsed();
        Class[] paramTypes = new Class[numArgs];

        for (int i = 0; i < numArgs; i++) {
            DragonFormalParameter param = (DragonFormalParameter) jjtGetChild(i);
            paramTypes[i] = (Class) param.eval(callstack, dragonBasicInterpreter);
        }

        this.paramTypes = paramTypes;

        return paramTypes;
    }
}

