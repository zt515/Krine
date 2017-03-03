package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.InterpreterException;

public class DelayedEvalDragonMethod extends DragonMethod {
    String returnTypeDescriptor;
    DragonReturnType returnTypeNode;
    String[] paramTypeDescriptors;
    DragonFormalParameters paramTypesNode;

    // used for the delayed evaluation...
    transient CallStack callStack;
    transient DragonBasicInterpreter dragonBasicInterpreter;

    /**
     * This constructor is used in class generation.  It supplies String type
     * descriptors for return and parameter class types and allows delay of
     * the evaluation of those types until they are requested.  It does this
     * by holding DragonType nodes, as well as an evaluation callStack, and
     * dragonBasicInterpreter which are called when the class types are requested.
     */
    /*
        Note: technically I think we could get by passing in only the
		current namespace or perhaps DragonClassManager here instead of
		CallStack and DragonInterpreter.  However let's just play it safe in case
		of future changes - anywhere you eval a node you need these.
	*/
    DelayedEvalDragonMethod(
            String name,
            String returnTypeDescriptor, DragonReturnType returnTypeNode,
            String[] paramNames,
            String[] paramTypeDescriptors, DragonFormalParameters paramTypesNode,
            DragonBlock methodBody,
            NameSpace declaringNameSpace, Modifiers modifiers,
            CallStack callStack, DragonBasicInterpreter dragonBasicInterpreter
    ) {
        super(name, null/*returnType*/, paramNames, null/*paramTypes*/,
                methodBody, declaringNameSpace, modifiers);

        this.returnTypeDescriptor = returnTypeDescriptor;
        this.returnTypeNode = returnTypeNode;
        this.paramTypeDescriptors = paramTypeDescriptors;
        this.paramTypesNode = paramTypesNode;
        this.callStack = callStack;
        this.dragonBasicInterpreter = dragonBasicInterpreter;
    }

    public String getReturnTypeDescriptor() {
        return returnTypeDescriptor;
    }

    public Class getReturnType() {
        if (returnTypeNode == null)
            return null;

        // DragonType will cache the type for us
        try {
            return returnTypeNode.evalReturnType(callStack, dragonBasicInterpreter);
        } catch (EvalError e) {
            throw new InterpreterException("can't eval return type: " + e);
        }
    }

    public String[] getParamTypeDescriptors() {
        return paramTypeDescriptors;
    }

    public Class[] getParameterTypes() {
        // DragonFormalParameters will cache the type for us
        try {
            return (Class[]) paramTypesNode.eval(callStack, dragonBasicInterpreter);
        } catch (EvalError e) {
            throw new InterpreterException("can't eval param types: " + e);
        }
    }
}
