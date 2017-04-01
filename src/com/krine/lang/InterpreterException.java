package com.krine.lang;

/**
 * An internal error in the krineBasicInterpreter has occurred.
 */
public class InterpreterException extends RuntimeException {

    public InterpreterException(final String s) {
        super(s);
    }


    public InterpreterException(final String s, final Throwable cause) {
        super(s, cause);
    }


}

