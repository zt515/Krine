package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

public class DragonPackageDeclaration extends SimpleNode {

    public DragonPackageDeclaration(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        DragonAmbiguousName name = (DragonAmbiguousName) jjtGetChild(0);
        NameSpace namespace = callstack.top();
        namespace.setPackage(name.text);
        // import the package we're in by default...
        namespace.importPackage(name.text);
        return Primitive.VOID;
    }
}
