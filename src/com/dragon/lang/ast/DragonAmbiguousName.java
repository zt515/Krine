package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.InterpreterException;
import com.dragon.lang.UtilEvalException;

class DragonAmbiguousName extends SimpleNode {
    public String text;

    DragonAmbiguousName(int id) {
        super(id);
    }

    public Name getName(NameSpace namespace) {
        return namespace.getNameResolver(text);
    }

    public Object toObject(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        return toObject(callstack, dragonBasicInterpreter, false);
    }

    Object toObject(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, boolean forceClass)
            throws EvalError {
        try {
            return
                    getName(callstack.top()).toObject(
                            callstack, dragonBasicInterpreter, forceClass);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

    public Class toClass(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        try {
            return getName(callstack.top()).toClass();
        } catch (ClassNotFoundException e) {
            throw new EvalError(e.getMessage(), this, callstack, e);
        } catch (UtilEvalException e2) {
            // ClassPathException is a type of UtilEvalException
            throw e2.toEvalError(this, callstack);
        }
    }

    public LeftValue toLHS(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        try {
            return getName(callstack.top()).toLeftValue(callstack, dragonBasicInterpreter);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /*
        The interpretation of an ambiguous name is context sensitive.
        We disallow a generic eval( ).
    */
    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        throw new InterpreterException(
                "Don't know how to eval an ambiguous name!"
                        + "  Use toObject() if you want an object.");
    }

    public String toString() {
        return "AmbigousName: " + text;
    }
}

