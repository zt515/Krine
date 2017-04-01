package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;
import com.krine.lang.UtilEvalException;

class KrineImportDeclaration extends SimpleNode {
    public boolean importPackage;
    public boolean staticImport;
    public boolean superImport;

    KrineImportDeclaration(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        NameSpace namespace = callstack.top();
        if (superImport)
            try {
                namespace.doSuperImport();
            } catch (UtilEvalException e) {
                throw e.toEvalError(this, callstack);
            }
        else {
            if (staticImport) {
                if (importPackage) {
                    Class clas = ((KrineAmbiguousName) jjtGetChild(0)).toClass(
                            callstack, krineBasicInterpreter);
                    namespace.importStatic(clas);
                } else
                    throw new EvalError(
                            "static field imports not supported yet",
                            this, callstack);
            } else {
                String name = ((KrineAmbiguousName) jjtGetChild(0)).text;
                if (importPackage)
                    namespace.importPackage(name);
                else
                    namespace.importClass(name);
            }
        }

        return Primitive.VOID;
    }
}

