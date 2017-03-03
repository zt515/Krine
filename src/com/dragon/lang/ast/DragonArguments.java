package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonArguments extends SimpleNode {
    DragonArguments(int id) {
        super(id);
    }

    /**
     * This node holds a set of arguments for a method invocation or
     * constructor call.
     * <p>
     * Note: arguments are not currently allowed to be VOID.
     */
    /*
        Disallowing VOIDs here was an easy way to support the throwing of a
		more descriptive error message on use of an undefined argument to a 
		method call (very common).  If it ever turns out that we need to 
		support that for some reason we'll have to re-evaluate how we get 
		"meta-information" about the arguments in the various invoke() methods 
		that take Object [].  We could either pass DragonArguments down to
		overloaded forms of the methods or throw an exception subtype 
		including the argument position back up, where the error message would
		be compounded.
	*/
    public Object[] getArguments(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        // evaluate each child
        Object[] args = new Object[jjtGetNumChildren()];
        for (int i = 0; i < args.length; i++) {
            args[i] = ((SimpleNode) jjtGetChild(i)).eval(callstack, dragonBasicInterpreter);
            if (args[i] == Primitive.VOID)
                throw new EvalError("Undefined argument: " +
                        ((SimpleNode) jjtGetChild(i)).getText(), this, callstack);
        }

        return args;
    }
}

