package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.InterpreterException;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

/**
 * DragonTargetException is an EvalError that wraps an exception thrown by the script
 * (or by code called from the script).  TargetErrors indicate exceptions
 * which can be caught within the script itself, whereas a general EvalError
 * indicates that the script cannot be evaluated further for some reason.
 * <p>
 * If the exception is caught within the script it is automatically unwrapped,
 * so the code looks like normal Java code.  If the DragonTargetException is thrown
 * from the eval() or dragonBasicInterpreter.eval() method it may be caught and unwrapped
 * to determine what exception was thrown.
 */
public final class DragonTargetException extends EvalError {
    private final boolean inNativeInterface;

    public DragonTargetException(
            String msg, Throwable t, SimpleNode node, CallStack callstack,
            boolean inNativeInterface) {
        super(msg, node, callstack, t);
        this.inNativeInterface = inNativeInterface;
    }

    public DragonTargetException(Throwable t, SimpleNode node, CallStack callstack) {
        this("DragonTargetException", t, node, callstack, false);
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
     * We are shielded from compile problems by using a dragon script.
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
        DragonBasicInterpreter i = new DragonBasicInterpreter();
        try {
            i.set("target", t);
            return (String) i.eval(getTarget);
        } catch (EvalError e) {
            throw new InterpreterException("xprintarget: " + e.toString());
        }
    }

    /**
     * Return true if the DragonTargetException was generated from native code.
     * e.g. if the script called into a compiled java class which threw
     * the excpetion.  We distinguish so that we can print the stack trace
     * for the native code case... the stack trace would not be useful if
     * the exception was generated by the script.  e.g. if the script
     * explicitly threw an exception... (the stack trace would simply point
     * to the dragon internals which generated the exception).
     */
    public boolean exceptionInNative() {
        return inNativeInterface;
    }
}
