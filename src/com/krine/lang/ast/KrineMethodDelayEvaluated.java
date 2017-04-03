package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

public class KrineMethodDelayEvaluated extends KrineMethod {
    String returnTypeDescriptor;
    KrineReturnType returnTypeNode;
    String[] paramTypeDescriptors;
    KrineFormalParameters paramTypesNode;

    // used for the delayed evaluation...
    transient CallStack callStack;
    transient KrineBasicInterpreter krineBasicInterpreter;

    /**
     * This constructor is used in class generation.  It supplies String type
     * descriptors for return and parameter class types and allows delay of
     * the evaluation of those types until they are requested.  It does this
     * by holding KrineType nodes, as well as an evaluation callStack, and
     * krineBasicInterpreter which are called when the class types are requested.
     */
    /*
        Note: technically I think we could get by passing in only the
		current namespace or perhaps KrineClassManager here instead of
		CallStack and KrineInterpreter.  However let's just play it safe in case
		of future changes - anywhere you eval a node you need these.
	*/
    KrineMethodDelayEvaluated(
            String name,
            String returnTypeDescriptor, KrineReturnType returnTypeNode,
            String[] paramNames,
            String[] paramTypeDescriptors, KrineFormalParameters paramTypesNode,
            KrineBlock methodBody,
            NameSpace declaringNameSpace, Modifiers modifiers,
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter
    ) {
        super(name, null/*returnType*/, paramNames, null/*paramTypes*/,
                methodBody, declaringNameSpace, modifiers);

        this.returnTypeDescriptor = returnTypeDescriptor;
        this.returnTypeNode = returnTypeNode;
        this.paramTypeDescriptors = paramTypeDescriptors;
        this.paramTypesNode = paramTypesNode;
        this.callStack = callStack;
        this.krineBasicInterpreter = krineBasicInterpreter;
    }

    public String getReturnTypeDescriptor() {
        return returnTypeDescriptor;
    }

    public Class getReturnType() {
        if (returnTypeNode == null)
            return null;

        // KrineType will cache the type for us
        try {
            return returnTypeNode.evalReturnType(callStack, krineBasicInterpreter);
        } catch (EvalError e) {
            throw new InterpreterException("can't eval return type: " + e);
        }
    }

    public String[] getParamTypeDescriptors() {
        return paramTypeDescriptors;
    }

    public Class[] getParameterTypes() {
        // KrineFormalParameters will cache the type for us
        try {
            return (Class[]) paramTypesNode.eval(callStack, krineBasicInterpreter);
        } catch (EvalError e) {
            throw new InterpreterException("can't eval param types: " + e);
        }
    }
}
