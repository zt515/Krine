package com.krine.command;

import com.krine.debugger.DefaultDebugger;
import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.KrineTargetException;

import java.lang.reflect.InvocationTargetException;

public class Main {

    public static void main(String[] args) {
        Argument argument = new Argument(args);
        String[] rest = argument.getRest();

        // No file name
        if (rest.length < 1) {
            showUsage();
            return;
        }

        String fileName = rest[0];
        KrineInterpreter interpreter = new KrineInterpreter();
        interpreter.setUnchecked("krine.args", rest);

        if (argument.isDebug()) {
            DefaultDebugger debugger = new DefaultDebugger(interpreter);
            debugger.startDebugging();
        }

        try {
            Object result = interpreter.source(fileName, interpreter.getGlobalNameSpace());

            if (result instanceof Class) {
                try {
                    KrineInterpreter.invokeMain((Class) result, rest);
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
        System.out.println("Usage: krine [-g] [fileName]");
    }
}
