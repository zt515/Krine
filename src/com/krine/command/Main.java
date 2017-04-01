package com.krine.command;

import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.KrineTargetException;
import com.krine.lang.debugger.KrineDebugger;

import java.lang.reflect.InvocationTargetException;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            showUsage();
            return;
        }

        String fileName = args[0];
        String[] dragonArgs;

        if (args.length > 1) {
            dragonArgs = new String[args.length - 1];
            System.arraycopy(args, 1, dragonArgs, 0, args.length - 1);
        } else {
            dragonArgs = new String[0];
        }

        KrineInterpreter dragon = new KrineInterpreter();
        dragon.setUnchecked("krine.args", dragonArgs);

        KrineDebugger debugger = new KrineDebugger(dragon);
        debugger.addBreakPoint(fileName)
                .add(3)
                .add(4)
                .add(8)
                .add(11)
                .add(12);
        debugger.startDebugging();

        try {
            Object result = dragon.source(fileName, dragon.getGlobalNameSpace());

            if (result instanceof Class) {
                try {
                    KrineInterpreter.invokeMain((Class) result, dragonArgs);
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
        System.out.println("Usage: Krine [fileName]");
    }
}
