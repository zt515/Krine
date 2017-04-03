package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

class KrineTypedVariableDeclaration extends SimpleNode {
    public Modifiers modifiers;

    KrineTypedVariableDeclaration(int id) {
        super(id);
    }

    private KrineType getTypeNode() {
        return ((KrineType) jjtGetChild(0));
    }

    Class evalType(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        KrineType typeNode = getTypeNode();
        return typeNode.getType(callStack, krineBasicInterpreter);
    }

    KrineVariableDeclarator[] getDeclarators() {
        int n = jjtGetNumChildren();
        int start = 1;
        KrineVariableDeclarator[] bvda = new KrineVariableDeclarator[n - start];
        for (int i = start; i < n; i++) {
            bvda[i - start] = (KrineVariableDeclarator) jjtGetChild(i);
        }
        return bvda;
    }

    /**
     * evaluate the type and one or more variable declarators, e.g.:
     * int a, b=5, c;
     */
    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        try {
            NameSpace namespace = callStack.top();
            KrineType typeNode = getTypeNode();
            Class type = typeNode.getType(callStack, krineBasicInterpreter);

            KrineVariableDeclarator[] bvda = getDeclarators();
            for (KrineVariableDeclarator dec : bvda) {
                // Type node is passed down the chain for array initializers
                // which need it under some circumstances
                Object value = dec.eval(typeNode, callStack, krineBasicInterpreter);

                try {
                    namespace.setTypedVariable(
                            dec.name, type, value, modifiers);
                } catch (UtilEvalException e) {
                    throw e.toEvalError(this, callStack);
                }
            }
        } catch (EvalError e) {
            e.reThrow("Typed variable declaration");
        }

        return Primitive.VOID;
    }

    public String getTypeDescriptor(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, String defaultPackage) {
        return getTypeNode().getTypeDescriptor(
                callStack, krineBasicInterpreter, defaultPackage);
    }
}
