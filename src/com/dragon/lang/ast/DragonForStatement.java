package com.dragon.lang.ast;

import com.dragon.lang.utils.CallStack;
import com.dragon.lang.DragonBasicInterpreter;

/**
 * Implementation of the for(;;) statement.
 */
class DragonForStatement extends SimpleNode implements ParserConstants {
    public boolean hasForInit;
    public boolean hasExpression;
    public boolean hasForUpdate;

    private SimpleNode forInit;
    private SimpleNode expression;
    private SimpleNode forUpdate;
    private SimpleNode statement;

    private boolean parsed;

    DragonForStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
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
			(which is correct Java behavior... see forscope4.dragon)
		*/

        // put forNameSpace it on the top of the stack
        // Note: it's important that there is only one exit point from this
        // method so that we can swap back the namespace.
        callstack.swap(forNameSpace);

        // Do the for init
        if (hasForInit)
            forInit.eval(callstack, dragonBasicInterpreter);

        Object returnControl = Primitive.VOID;
        while (true) {
            if (hasExpression) {
                boolean cond = DragonIfStatement.evaluateCondition(
                        expression, callstack, dragonBasicInterpreter);

                if (!cond)
                    break;
            }

            boolean breakout = false; // switch eats a multi-level break here?
            if (statement != null) // not empty statement
            {
                // do *not* invoke special override for block... (see above)
                Object ret = statement.eval(callstack, dragonBasicInterpreter);

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
                forUpdate.eval(callstack, dragonBasicInterpreter);
        }

        callstack.swap(enclosingNameSpace);  // put it back
        return returnControl;
    }

}
