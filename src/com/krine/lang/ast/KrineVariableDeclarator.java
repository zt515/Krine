package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

/**
 * name [ = initializer ]
 * evaluate name and return optional initializer
 */
class KrineVariableDeclarator extends SimpleNode {
    // The token.image text of the name... never changes.
    public String name;

    KrineVariableDeclarator(int id) {
        super(id);
    }

    /**
     * Evaluate the optional initializer value.
     * (The name was set at parse time.)
     * <p>
     * A variable declarator can be evaluated with or without preceding
     * type information. Currently the type info is only used by array
     * initializers in the case where there is no explicitly declared type.
     *
     * @param typeNode is the KrineType node.  Its info is passed through to any
     *                 variable initializer children for the case where the array initializer
     *                 does not declare the type explicitly. e.g.
     *                 int [] a = { 1, 2 };
     *                 typeNode may be null to indicate no type information available.
     */
    public Object eval(
            KrineType typeNode, CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        // null value means no value
        Object value = null;

        if (jjtGetNumChildren() > 0) {
            SimpleNode initializer = (SimpleNode) jjtGetChild(0);

			/*
                If we have type info and the child is an array initializer
				pass it along...  Else use the default eval style.
				(This allows array initializer to handle the problem... 
				allowing for future enhancements in loosening types there).	
			*/
            if ((typeNode != null)
                    && initializer instanceof KrineArrayInitializer
                    )
                value = ((KrineArrayInitializer) initializer).eval(
                        typeNode.getBaseType(), typeNode.getArrayDims(),
                        callStack, krineBasicInterpreter);
            else
                value = initializer.eval(callStack, krineBasicInterpreter);
        }

        if (value == Primitive.VOID)
            throw new EvalError("Void initializer.", this, callStack);

        return value;
    }

    public String toString() {
        return "KrineVariableDeclarator " + name;
    }
}
