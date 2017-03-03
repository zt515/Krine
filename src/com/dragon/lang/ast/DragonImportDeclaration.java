package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.UtilEvalException;

class DragonImportDeclaration extends SimpleNode {
    public boolean importPackage;
    public boolean staticImport;
    public boolean superImport;

    DragonImportDeclaration(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
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
                    Class clas = ((DragonAmbiguousName) jjtGetChild(0)).toClass(
                            callstack, dragonBasicInterpreter);
                    namespace.importStatic(clas);
                } else
                    throw new EvalError(
                            "static field imports not supported yet",
                            this, callstack);
            } else {
                String name = ((DragonAmbiguousName) jjtGetChild(0)).text;
                if (importPackage)
                    namespace.importPackage(name);
                else
                    namespace.importClass(name);
            }
        }

        return Primitive.VOID;
    }
}

