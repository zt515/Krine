package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

/**
 * KrineTargetException is an EvalError that wraps an exception thrown by the script
 * (or by code called from the script).  TargetErrors indicate exceptions
 * which can be caught within the script itself, whereas a general EvalError
 * indicates that the script cannot be evaluated further for some reason.
 * <p>
 * If the exception is caught within the script it is automatically unwrapped,
 * so the code looks like normal Java code.  If the KrineTargetException is thrown
 * from the eval() or krineBasicInterpreter.eval() method it may be caught and unwrapped
 * to determine what exception was thrown.
 */
public final class KrineTargetException extends EvalError {
    private final boolean inNativeInterface;

    public KrineTargetException(
            String msg, Throwable t, SimpleNode node, CallStack callStack,
            boolean inNativeInterface) {
        super(msg, node, callStack, t);
        this.inNativeInterface = inNativeInterface;
    }

    public KrineTargetException(Throwable t, SimpleNode node, CallStack callStack) {
        this("KrineTargetException", t, node, callStack, false);
    }

    public Throwable getTarget() {
        // check for easy mistake
        Throwable target = getCause();
        if (target instanceof InvocationTargetException)
            return ((InvocationTargetException) target).getTargetException();
        else
            return target;
    }

    public String getMessage() {
        return super.getMessage()
                + "\nTarget exception: " +
                printTargetError(getCause());
    }

    public void printStackTrace(boolean debug, PrintStream out) {
        if (debug) {
            super.printStackTrace(out);
            out.println("--- Target Stack Trace ---");
        }
        getCause().printStackTrace(out);
    }

    /**
     * Generate a printable string showing the wrapped target exception.
     * If the proxy mechanism is available, allow the extended print to
     * check for UndeclaredThrowableException and print that embedded error.
     */
    private String printTargetError(Throwable t) {
        return getCause().toString() + "\n" + xPrintTargetError(t);
    }

    /**
     * Extended form of print target error.
     * This indirection is used to print UndeclaredThrowableExceptions
     * which are possible when the proxy mechanism is available.
     * <p>
     * We are shielded from compile problems by using a krine script.
     * This is acceptable here because we're not in a critical path...
     * Otherwise we'd need yet another dynamically loaded module just for this.
     */
    private String xPrintTargetError(Throwable t) {
        String getTarget =
                "import java.lang.reflect.UndeclaredThrowableException;" +
                        "String result=\"\";" +
                        "while ( target instanceof UndeclaredThrowableException ) {" +
                        "	target=target.getUndeclaredThrowable(); " +
                        "	result+=\"Nested: \"+target.toString();" +
                        "}" +
                        "return result;";
        KrineBasicInterpreter i = new KrineBasicInterpreter();
        try {
            i.set("target", t);
            return (String) i.eval(getTarget);
        } catch (EvalError e) {
            throw new InterpreterException("XPrintTarget: " + e.toString());
        }
    }

    /**
     * Return true if the KrineTargetException was generated from native code.
     * e.g. if the script called into a compiled java class which threw
     * the exception.  We distinguish so that we can print the stack trace
     * for the native code case... the stack trace would not be useful if
     * the exception was generated by the script.  e.g. if the script
     * explicitly threw an exception... (the stack trace would simply point
     * to the krine internals which generated the exception).
     */
    public boolean exceptionInNative() {
        return inNativeInterface;
    }
}

