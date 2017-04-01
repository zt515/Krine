package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;
import com.krine.lang.InterpreterException;
import com.krine.lang.UtilEvalException;

class KrineAmbiguousName extends SimpleNode {
    public String text;

    KrineAmbiguousName(int id) {
        super(id);
    }

    public Name getName(NameSpace namespace) {
        return namespace.getNameResolver(text);
    }

    public Object toObject(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        return toObject(callstack, krineBasicInterpreter, false);
    }

    Object toObject(
            CallStack callstack, KrineBasicInterpreter krineBasicInterpreter, boolean forceClass)
            throws EvalError {
        try {
            return
                    getName(callstack.top()).toObject(
                            callstack, krineBasicInterpreter, forceClass);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

    public Class toClass(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
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

    public LeftValue toLHS(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        try {
            return getName(callstack.top()).toLeftValue(callstack, krineBasicInterpreter);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }

    /*
        The interpretation of an ambiguous name is context sensitive.
        We disallow a generic eval( ).
    */
    public Object eval(CallStack callstack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        throw new InterpreterException(
                "Don't know how to eval an ambiguous name!"
                        + "  Use toObject() if you want an object.");
    }

    public String toString() {
        return "AmbigousName: " + text;
    }
}

