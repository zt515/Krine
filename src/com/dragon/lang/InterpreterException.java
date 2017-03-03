package com.dragon.lang;

/**
 * An internal error in the dragonInterpreterInternal has occurred.
 */
public class InterpreterException extends RuntimeException {

    public InterpreterException(final String s) {
        super(s);
    }


    public InterpreterException(final String s, final Throwable cause) {
        super(s, cause);
    }


}

