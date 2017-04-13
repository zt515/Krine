package com.krine.command;

import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.KrineTargetException;

import java.lang.reflect.InvocationTargetException;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            showUsage();
            return;
        }

        String fileName = args[0];

        KrineInterpreter interpreter = new KrineInterpreter();
        interpreter.setUnchecked("krine.args", args);

        try {
            Object result = interpreter.source(fileName, interpreter.getGlobalNameSpace());

            if (result instanceof Class) {
                try {
                    KrineInterpreter.invokeMain((Class) result, args);
                } catch (Exception e) {
                    Object o = e;
                    if (e instanceof InvocationTargetException) {
                        o = ((InvocationTargetException) e)
                                .getTargetException();
                    }

                    System.err.println(
                            "Class: " + result + " main method threw exception:" + o);
                }
            }
        } catch (KrineTargetException e) {
            if (e.exceptionInNative()) {
                e.printStackTrace(System.err);
            }

        } catch (Throwable e) {
            System.err.println(e.getClass().getCanonicalName() + ": " + e);
        }


    }

    private static void showUsage() {
        System.out.println("Usage: krine [fileName]");
    }
}
