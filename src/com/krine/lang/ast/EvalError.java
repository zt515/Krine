package com.krine.lang.ast;

import com.krine.lang.utils.CallStack;

import java.util.Locale;

/**
 * EvalError indicates that we cannot continue evaluating the script
 * or the script has thrown an exception.
 * <p>
 * EvalError may be thrown for a script syntax error, an evaluation
 * error such as referring to an undefined variable, an internal error.
 * <p>
 *
 * @see KrineTargetException
 */
public class EvalError extends Exception {
    private SimpleNode node;

    // Note: no way to mutate the Throwable message, must maintain our own
    private String message;

    private final CallStack callStack;

    public EvalError(String s, SimpleNode node, CallStack callStack, Throwable cause) {
        this(s, node, callStack);
        initCause(cause);
    }

    public EvalError(String s, SimpleNode node, CallStack callStack) {
        this.message = s;
        this.node = node;
        // freeze the callStack for the stack trace.
        this.callStack = callStack == null ? null : callStack.copy();
    }

    /**
     * Print the error with line number and stack trace.
     */
    public String getMessage() {
        StringBuilder traceBuilder = new StringBuilder();

        if (node != null) {
            traceBuilder.append(String.format(Locale.getDefault(),
                    "<In file '%s:%d'>: ", node.getSourceFile(), node.getLineNumber()));
        } else {
            traceBuilder.append("<at unknown location>: ");
        }

        traceBuilder.append(getRawMessage())
                .append(String.format(Locale.getDefault(), "\n\tcode: %s\n", node.getText()));

        if (callStack != null) {
            traceBuilder.append(getScriptStackTrace());
        }

        return traceBuilder.toString();
    }

    /**
     * Re-throw the error, prepending the specified message.
     */
    public void reThrow(String msg)
            throws EvalError {
        prependMessage(msg);
        throw this;
    }

    /**
     * The error has trace info associated with it.
     * i.e. It has an AST node that can print its location and source text.
     */
    public SimpleNode getNode() {
        return node;
    }

    public void setNode(SimpleNode node) {
        this.node = node;
    }

    public String getErrorText() {
        if (node != null)
            return node.getText();
        else
            return "<unknown error>";
    }

    public int getErrorLineNumber() {
        if (node != null)
            return node.getLineNumber();
        else
            return -1;
    }

    public String getErrorSourceFile() {
        if (node != null)
            return node.getSourceFile();
        else
            return "<unknown file>";
    }

    public String getScriptStackTrace() {
        if (callStack == null)
            return "<Unknown>";

        StringBuilder traceBuilder = new StringBuilder();

        CallStack stack = callStack.copy();
        while (stack.depth() > 0) {
            NameSpace ns = stack.pop();
            SimpleNode node = ns.getNode();

            if (ns.isMethod) {
                traceBuilder.append("\nCalled from method: ");
                traceBuilder.append(ns.getPackage() != null ? ns.getPackage() + "." : "");
                traceBuilder.append(ns.getName());

                if (node != null) {
                    traceBuilder.append("at ")
                            .append(node.getSourceFile())
                            .append(':')
                            .append(node.getLineNumber())
                            .append(':')
                            .append(node.getText());
                }
            }
        }

        return traceBuilder.toString();
    }

    public String getRawMessage() {
        return message;
    }

    /**
     * Prepend the message if it is non-null.
     */
    private void prependMessage(String s) {
        if (s == null)
            return;

        if (message == null)
            message = s;
        else
            message = s + " : " + message;
    }

}

