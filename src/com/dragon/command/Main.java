package com.dragon.command;

import com.dragon.interpreter.DragonInterpreter;
import com.dragon.lang.ast.DragonTargetException;
import com.dragon.lang.debugger.DragonDebugger;

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

        DragonInterpreter dragon = new DragonInterpreter();
        dragon.setUnchecked("dragon.args", dragonArgs);

        DragonDebugger debugger = new DragonDebugger(dragon);
        debugger.addBreakPoint(fileName)
                .add(3)
                .add(9);
        debugger.startDebugging();

        try {
            Object result = dragon.source(fileName, dragon.getGlobalNameSpace());

            if (result instanceof Class) {
                try {
                    DragonInterpreter.invokeMain((Class) result, dragonArgs);
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
        } catch (DragonTargetException e) {
            if (e.exceptionInNative()) {
                e.printStackTrace(System.err);
            }

        } catch (Throwable e) {
            System.err.println(e.getClass().getCanonicalName() + ": " + e);
        }


    }

    private static void showUsage() {
        System.out.println("Usage: Dragon [fileName]");
    }
}
