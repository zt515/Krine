package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

/**
 * Implementation of the for(;;) statement.
 */
class KrineForStatement extends SimpleNode implements ParserConstants {
    public boolean hasForInit;
    public boolean hasExpression;
    public boolean hasForUpdate;

    private SimpleNode forInit;
    private SimpleNode expression;
    private SimpleNode forUpdate;
    private SimpleNode statement;

    private boolean parsed;

    KrineForStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();
        int i = 0;
        if (hasForInit)
            forInit = ((SimpleNode) jjtGetChild(i++));
        if (hasExpression)
            expression = ((SimpleNode) jjtGetChild(i++));
        if (hasForUpdate)
            forUpdate = ((SimpleNode) jjtGetChild(i++));
        if (i < jjtGetNumChildren()) // should normally be
            statement = ((SimpleNode) jjtGetChild(i));

        NameSpace enclosingNameSpace = callstack.top();
        BlockNameSpace forNameSpace = new BlockNameSpace(enclosingNameSpace);

		/*
            Note: some interesting things are going on here.

			1) We swap instead of push...  The primary mode of operation 
			acts like we are in the enclosing namespace...  (super must be 
			preserved, etc.)

			2) We do *not* call the body block eval with the namespace 
			override.  Instead we allow it to create a second subordinate 
			BlockNameSpace child of the forNameSpace.  Variable propogation 
			still works through the chain, but the block's child cleans the 
			state between iteration.  
			(which is correct Java behavior... see forscope4.krine)
		*/

        // put forNameSpace it on the top of the stack
        // Note: it's important that there is only one exit point from this
        // method so that we can swap back the namespace.
        callstack.swap(forNameSpace);

        // Do the for init
        if (hasForInit)
            forInit.eval(callstack, krineBasicInterpreter);

        Object returnControl = Primitive.VOID;
        while (true) {
            if (hasExpression) {
                boolean cond = KrineIfStatement.evaluateCondition(
                        expression, callstack, krineBasicInterpreter);

                if (!cond)
                    break;
            }

            boolean breakout = false; // switch eats a multi-level break here?
            if (statement != null) // not empty statement
            {
                // do *not* invoke special override for block... (see above)
                Object ret = statement.eval(callstack, krineBasicInterpreter);

                if (ret instanceof ReturnControl) {
                    switch (((ReturnControl) ret).kind) {
                        case RETURN:
                            returnControl = ret;
                            breakout = true;
                            break;

                        case CONTINUE:
                            break;

                        case BREAK:
                            breakout = true;
                            break;
                    }
                }
            }

            if (breakout)
                break;

            if (hasForUpdate)
                forUpdate.eval(callstack, krineBasicInterpreter);
        }

        callstack.swap(enclosingNameSpace);  // put it back
        return returnControl;
    }

}
