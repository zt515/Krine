package com.krine.lang.ast;

import com.krine.lang.utils.CallStack;
import com.krine.lang.KrineBasicInterpreter;

class KrineFormalParameters extends SimpleNode {
    private String[] paramNames;
    /**
     * For loose type parameters the paramTypes are null.
     */
    // unsafe caching of types
    Class[] paramTypes;
    int numArgs;
    String[] typeDescriptors;

    KrineFormalParameters(int id) {
        super(id);
    }

    void insureParsed() {
        if (paramNames != null)
            return;

        this.numArgs = jjtGetNumChildren();
        String[] paramNames = new String[numArgs];

        for (int i = 0; i < numArgs; i++) {
            krineFormalParameter param = (krineFormalParameter) jjtGetChild(i);
            paramNames[i] = param.name;
        }

        this.paramNames = paramNames;
    }

    public String[] getParamNames() {
        insureParsed();
        return paramNames;
    }

    public String[] getTypeDescriptors(
            CallStack callstack, KrineBasicInterpreter krineBasicInterpreter, String defaultPackage) {
        if (typeDescriptors != null)
            return typeDescriptors;

        insureParsed();
        String[] typeDesc = new String[numArgs];

        for (int i = 0; i < numArgs; i++) {
            krineFormalParameter param = (krineFormalParameter) jjtGetChild(i);
            typeDesc[i] = param.getTypeDescriptor(
                    callstack, krineBasicInterpreter, defaultPackage);
        }

        this.typeDescriptors = typeDesc;
        return typeDesc;
    }

    /**
     * Evaluate the types.
     * Note that type resolution does not require the krineBasicInterpreter instance.
     */
    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        if (paramTypes != null)
            return paramTypes;

        insureParsed();
        Class[] paramTypes = new Class[numArgs];

        for (int i = 0; i < numArgs; i++) {
            krineFormalParameter param = (krineFormalParameter) jjtGetChild(i);
            paramTypes[i] = (Class) param.eval(callstack, krineBasicInterpreter);
        }

        this.paramTypes = paramTypes;

        return paramTypes;
    }
}

