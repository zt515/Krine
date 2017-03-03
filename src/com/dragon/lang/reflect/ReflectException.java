package com.dragon.lang.reflect;

public class ReflectException extends Exception {
    public ReflectException() {
        super();
    }

    public ReflectException(String s) {
        super(s);
    }

    public ReflectException(String s, Throwable t) {
        super(s, t);
    }
}
