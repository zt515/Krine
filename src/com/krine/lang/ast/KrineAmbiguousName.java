package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

class KrineAmbiguousName extends SimpleNode {
    public String text;

    KrineAmbiguousName(int id) {
        super(id);
    }

    public Name getName(NameSpace namespace) {
        return namespace.getNameResolver(text);
    }

    public Object toObject(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        return toObject(callStack, krineBasicInterpreter, false);
    }

    Object toObject(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, boolean forceClass)
            throws EvalError {
        try {
            return
                    getName(callStack.top()).toObject(
                            callStack, krineBasicInterpreter, forceClass);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
    }

    public Class toClass(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        try {
            return getName(callStack.top()).toClass();
        } catch (ClassNotFoundException e) {
            throw new EvalError(e.getMessage(), this, callStack, e);
        } catch (UtilEvalException e2) {
            // ClassPathException is a type of UtilEvalException
            throw e2.toEvalError(this, callStack);
        }
    }

    public LeftValue toLHS(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        try {
            return getName(callStack.top()).toLeftValue(callStack, krineBasicInterpreter);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
    }

    /*
        The interpretation of an ambiguous name is context sensitive.
        We disallow a generic eval( ).
    */
    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        throw new InterpreterException(
                "Don't know how to eval an ambiguous name!"
                        + "  Use toObject() if you want an object.");
    }

    public String toString() {
        return "AmbiguousName: " + text;
    }
}

