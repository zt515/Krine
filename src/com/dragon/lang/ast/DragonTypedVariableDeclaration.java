package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.UtilEvalException;

class DragonTypedVariableDeclaration extends SimpleNode {
    public Modifiers modifiers;

    DragonTypedVariableDeclaration(int id) {
        super(id);
    }

    private DragonType getTypeNode() {
        return ((DragonType) jjtGetChild(0));
    }

    Class evalType(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        DragonType typeNode = getTypeNode();
        return typeNode.getType(callstack, dragonBasicInterpreter);
    }

    DragonVariableDeclarator[] getDeclarators() {
        int n = jjtGetNumChildren();
        int start = 1;
        DragonVariableDeclarator[] bvda = new DragonVariableDeclarator[n - start];
        for (int i = start; i < n; i++) {
            bvda[i - start] = (DragonVariableDeclarator) jjtGetChild(i);
        }
        return bvda;
    }

    /**
     * evaluate the type and one or more variable declarators, e.g.:
     * int a, b=5, c;
     */
    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        try {
            NameSpace namespace = callstack.top();
            DragonType typeNode = getTypeNode();
            Class type = typeNode.getType(callstack, dragonBasicInterpreter);

            DragonVariableDeclarator[] bvda = getDeclarators();
            for (int i = 0; i < bvda.length; i++) {
                DragonVariableDeclarator dec = bvda[i];

                // Type node is passed down the chain for array initializers
                // which need it under some circumstances
                Object value = dec.eval(typeNode, callstack, dragonBasicInterpreter);

                try {
                    namespace.setTypedVariable(
                            dec.name, type, value, modifiers);
                } catch (UtilEvalException e) {
                    throw e.toEvalError(this, callstack);
                }
            }
        } catch (EvalError e) {
            e.reThrow("Typed variable declaration");
        }

        return Primitive.VOID;
    }

    public String getTypeDescriptor(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        return getTypeNode().getTypeDescriptor(
                callstack, dragonBasicInterpreter, defaultPackage);
    }
}
