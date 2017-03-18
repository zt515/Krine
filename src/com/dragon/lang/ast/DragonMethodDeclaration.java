package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.UtilEvalException;

class DragonMethodDeclaration extends SimpleNode {
    public String name;

    // Begin Child node structure evaluated by insureNodesParsed

    DragonReturnType returnTypeNode;
    DragonFormalParameters paramsNode;
    DragonBlock blockNode;
    // index of the first throws clause child node
    int firstThrowsClause;

    // End Child node structure evaluated by insureNodesParsed

    public Modifiers modifiers;

    // Unsafe caching of type here.
    Class returnType;  // null (none), Void.TYPE, or a Class
    int numThrows = 0;

    DragonMethodDeclaration(int id) {
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
        if (firstNode instanceof DragonReturnType) {
            returnTypeNode = (DragonReturnType) firstNode;
            paramsNode = (DragonFormalParameters) jjtGetChild(1);
            if (jjtGetNumChildren() > 2 + numThrows)
                blockNode = (DragonBlock) jjtGetChild(2 + numThrows); // skip throws
            ++firstThrowsClause;
        } else {
            paramsNode = (DragonFormalParameters) jjtGetChild(0);
            blockNode = (DragonBlock) jjtGetChild(1 + numThrows); // skip throws
        }
    }

    /**
     * Evaluate the return type node.
     *
     * @return the type or null indicating loosely typed return
     */
    Class evalReturnType(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        insureNodesParsed();
        if (returnTypeNode != null)
            return returnTypeNode.evalReturnType(callstack, dragonBasicInterpreter);
        else
            return null;
    }

    String getReturnTypeDescriptor(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        insureNodesParsed();
        if (returnTypeNode == null)
            return null;
        else
            return returnTypeNode.getTypeDescriptor(
                    callstack, dragonBasicInterpreter, defaultPackage);
    }

    DragonReturnType getReturnTypeNode() {
        insureNodesParsed();
        return returnTypeNode;
    }

    /**
     * Evaluate the declaration of the method.  That is, determine the
     * structure of the method and install it into the caller's namespace.
     */
    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        returnType = evalReturnType(callstack, dragonBasicInterpreter);
        evalNodes(callstack, dragonBasicInterpreter);

        // Install an *instance* of this method in the namespace.
        // See notes in DragonMethod

// This is not good...
// need a way to update eval without re-installing...
// so that we can re-eval params, etc. when classloader changes
// look into this

        NameSpace namespace = callstack.top();
        DragonMethod dragonMethod = new DragonMethod(this, namespace, modifiers);
        try {
            namespace.setMethod(dragonMethod);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }

        return Primitive.VOID;
    }

    private void evalNodes(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        insureNodesParsed();

        // validate that the throws names are class names
        for (int i = firstThrowsClause; i < numThrows + firstThrowsClause; i++)
            ((DragonAmbiguousName) jjtGetChild(i)).toClass(
                    callstack, dragonBasicInterpreter);

        paramsNode.eval(callstack, dragonBasicInterpreter);

        // if strictJava mode, check for loose parameters and return type
        if (dragonBasicInterpreter.getStrictJava()) {
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
