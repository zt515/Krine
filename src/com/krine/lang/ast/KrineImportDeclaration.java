package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

class KrineImportDeclaration extends SimpleNode {
    public boolean importPackage;
    public boolean staticImport;
    public boolean superImport;

    KrineImportDeclaration(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        NameSpace nameSpace = callStack.top();
        if (superImport) {
            try {
                nameSpace.doSuperImport();
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callStack);
            }
        } else {
            if (staticImport) {
                if (importPackage) {
                    Class clazz = ((KrineAmbiguousName) jjtGetChild(0)).toClass(
                            callStack, krineBasicInterpreter);
                    nameSpace.importStatic(clazz);
                } else {
                    throw new EvalError(
                            "static leftValue imports not supported yet",
                            this, callStack);
                }
            } else {
                String name = ((KrineAmbiguousName) jjtGetChild(0)).text;
                if (importPackage) {
                    nameSpace.importPackage(name);
                    nameSpace.importPackageAsModule(krineBasicInterpreter, name);
                } else {
                    nameSpace.importClass(name);
                }
            }
        }

        return Primitive.VOID;
    }
}

