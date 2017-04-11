package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

class KrineMethodDeclaration extends SimpleNode {
    public String name;

    // Begin Child node structure evaluated by insureNodesParsed

    KrineReturnType returnTypeNode;
    KrineFormalParameters paramsNode;
    KrineBlock blockNode;
    // arrayIndex of the first throws clause child node
    int firstThrowsClause;

    // End Child node structure evaluated by insureNodesParsed

    public Modifiers modifiers;

    // Unsafe caching of type here.
    Class returnType;  // null (none), Void.TYPE, or a Class
    int numThrows = 0;

    KrineMethodDeclaration(int id) {
        super(id);
    }

    /**
     * Set the returnTypeNode, paramsNode, and blockNode based on child
     * node structure.  No evaluation is done here.
     */
    synchronized void insureNodesParsed() {
        if (paramsNode != null) // there is always a paramsNode
            return;

        Object firstNode = jjtGetChild(0);
        firstThrowsClause = 1;
        if (firstNode instanceof KrineReturnType) {
            returnTypeNode = (KrineReturnType) firstNode;
            paramsNode = (KrineFormalParameters) jjtGetChild(1);
            if (jjtGetNumChildren() > 2 + numThrows)
                blockNode = (KrineBlock) jjtGetChild(2 + numThrows); // skip throws
            ++firstThrowsClause;
        } else {
            paramsNode = (KrineFormalParameters) jjtGetChild(0);
            blockNode = (KrineBlock) jjtGetChild(1 + numThrows); // skip throws
        }
    }

    /**
     * Evaluate the return type node.
     *
     * @return the type or null indicating loosely typed return
     */
    Class evalReturnType(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        insureNodesParsed();
        if (returnTypeNode != null)
            return returnTypeNode.evalReturnType(callStack, krineBasicInterpreter);
        else
            return null;
    }

    String getReturnTypeDescriptor(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, String defaultPackage) {
        insureNodesParsed();
        if (returnTypeNode == null)
            return null;
        else
            return returnTypeNode.getTypeDescriptor(
                    callStack, krineBasicInterpreter, defaultPackage);
    }

    KrineReturnType getReturnTypeNode() {
        insureNodesParsed();
        return returnTypeNode;
    }

    /**
     * Evaluate the declaration of the method.  That is, determine the
     * structure of the method and install it into the caller's namespace.
     */
    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        returnType = evalReturnType(callStack, krineBasicInterpreter);
        evalNodes(callStack, krineBasicInterpreter);

        // Install an *instance* of this method in the namespace.
        // See notes in KrineMethod

        // This is not good...
        // need a way to update eval without re-installing...
        // so that we can re-eval params, etc. when classloader changes

        NameSpace namespace = callStack.top();
        KrineMethod krineMethod = new KrineMethod(this, namespace, modifiers);
        try {
            namespace.setMethod(krineMethod);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
        
        return Primitive.VOID;
    }

    private void evalNodes(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        insureNodesParsed();

        // validate that the throws names are class names
        for (int i = firstThrowsClause; i < numThrows + firstThrowsClause; i++)
            ((KrineAmbiguousName) jjtGetChild(i)).toClass(
                    callStack, krineBasicInterpreter);

        paramsNode.eval(callStack, krineBasicInterpreter);

        // if strictJava mode, check for loose parameters and return type
        if (krineBasicInterpreter.isStrictJava()) {
            for (int i = 0; i < paramsNode.paramTypes.length; i++)
                if (paramsNode.paramTypes[i] == null)
                    // Warning: Null callStack here.  Don't think we need
                    // a stack trace to indicate how we sourced the method.
                    throw new EvalError(
                            "(Strict Java Mode) Undeclared argument type, parameter: " +
                                    paramsNode.getParamNames()[i] + " in method: "
                                    + name, this, null);

            if (returnType == null)
                // Warning: Null callStack here.  Don't think we need
                // a stack trace to indicate how we sourced the method.
                throw new EvalError(
                        "(Strict Java Mode) Undeclared return type for method: "
                                + name, this, null);
        }
    }

    public String toString() {
        return "MethodDeclaration: " + name;
    }
}
