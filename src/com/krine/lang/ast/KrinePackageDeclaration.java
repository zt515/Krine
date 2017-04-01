package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

public class KrinePackageDeclaration extends SimpleNode {

    public KrinePackageDeclaration(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        KrineAmbiguousName name = (KrineAmbiguousName) jjtGetChild(0);
        NameSpace namespace = callstack.top();
        namespace.setPackage(name.text);
        // import the package we're in by default...
        namespace.importPackage(name.text);
        return Primitive.VOID;
    }
}
