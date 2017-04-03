package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.reflect.ReflectException;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.InvocationUtil;

import java.lang.reflect.InvocationTargetException;

class KrineMethodInvocation extends SimpleNode {
    KrineMethodInvocation(int id) {
        super(id);
    }

    KrineAmbiguousName getNameNode() {
        return (KrineAmbiguousName) jjtGetChild(0);
    }

    KrineArguments getArgsNode() {
        return (KrineArguments) jjtGetChild(1);
    }

    /**
     * Evaluate the method invocation with the specified callStack and
     * krineBasicInterpreter
     */
    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        NameSpace namespace = callStack.top();
        KrineAmbiguousName nameNode = getNameNode();

        // Do not evaluate methods this() or super() in class instance space
        // (i.e. inside a constructor)
        if (namespace.getParent() != null && namespace.getParent().isClass
                && (nameNode.text.equals("super") || nameNode.text.equals("this"))
                )
            return Primitive.VOID;

        Name name = nameNode.getName(namespace);
        Object[] args = getArgsNode().getArguments(callStack, krineBasicInterpreter);

        try {
            return name.invokeMethod(krineBasicInterpreter, args, callStack, this);
        } catch (ReflectException e) {
            throw new EvalError(
                    "Error in method invocation: " + e.getMessage(),
                    this, callStack, e);
        } catch (InvocationTargetException e) {
            throw InvocationUtil.newTargetException("Method Invocation " + name, this, callStack, e);

        } catch (UtilEvalException e) {
            throw e.toEvalError(this, callStack);
        }
    }
}

