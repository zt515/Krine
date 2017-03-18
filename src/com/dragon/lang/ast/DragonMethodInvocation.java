package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.reflect.ReflectException;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.UtilEvalException;

import java.lang.reflect.InvocationTargetException;

class DragonMethodInvocation extends SimpleNode {
    DragonMethodInvocation(int id) {
        super(id);
    }

    DragonAmbiguousName getNameNode() {
        return (DragonAmbiguousName) jjtGetChild(0);
    }

    DragonArguments getArgsNode() {
        return (DragonArguments) jjtGetChild(1);
    }

    /**
     * Evaluate the method invocation with the specified callStack and
     * dragonBasicInterpreter
     */
    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        NameSpace namespace = callstack.top();
        DragonAmbiguousName nameNode = getNameNode();

        // Do not evaluate methods this() or super() in class instance space
        // (i.e. inside a constructor)
        if (namespace.getParent() != null && namespace.getParent().isClass
                && (nameNode.text.equals("super") || nameNode.text.equals("this"))
                )
            return Primitive.VOID;

        Name name = nameNode.getName(namespace);
        Object[] args = getArgsNode().getArguments(callstack, dragonBasicInterpreter);

// This try/catch block is replicated is DragonPrimarySuffix... need to
// factor out common functionality...
// Move to Reflect?
        try {
            return name.invokeMethod(dragonBasicInterpreter, args, callstack, this);
        } catch (ReflectException e) {
            throw new EvalError(
                    "Error in method invocation: " + e.getMessage(),
                    this, callstack, e);
        } catch (InvocationTargetException e) {
            String msg = "Method Invocation " + name;
            Throwable te = e.getTargetException();

			/*
                Try to squeltch the native code stack trace if the exception
				was caused by a reflective call back into the dragon dragonBasicInterpreter
				(e.g. eval() or source()
			*/
            boolean isNative = true;
            if (te instanceof EvalError)
                if (te instanceof DragonTargetException)
                    isNative = ((DragonTargetException) te).exceptionInNative();
                else
                    isNative = false;

            throw new DragonTargetException(msg, te, this, callstack, isNative);
        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callstack);
        }
    }
}

