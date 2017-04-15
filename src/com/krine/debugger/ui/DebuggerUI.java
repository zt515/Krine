package com.krine.debugger.ui;

import com.krine.debugger.BreakPoint;
import com.krine.debugger.IDebugger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Set;

/**
 * @author kiva
 * @date 2017/4/16
 */
public class DebuggerUI {
    private IDebugger debugger;
    private BufferedReader reader;

    public DebuggerUI(IDebugger debugger, InputStream commandInput) {
        this.debugger = debugger;
        this.reader = new BufferedReader(new InputStreamReader(commandInput));
    }

    public void shutdown() {
        try {
            reader.close();
        } catch (IOException ignore) {
        }
    }

    public void println(Object... args) {
        // Use System.err
        for (Object arg : args) {
            System.err.print(arg.toString());
            System.err.print(" ");
        }
        System.err.println();
    }

    public void printf(String format, Object... args) {
        System.err.printf(Locale.getDefault(), format, args);
    }

    public void showUI(BreakPoint breakPoint) {
        if (breakPoint != null) {
            println(breakPoint);
            println("     " + breakPoint.getCode());
        }

        try {
            boolean next = false;
            String s;
            while (!next) {
                printf("ddb> ");

                if ((s = reader.readLine()) == null) {
                    break;
                }
                String[] c = s.split(" ");

                switch (c[0]) {
                    case "detach":
                        debugger.detach();
                        // no break;
                    case "n":
                    case "next":
                        next = true;
                        break;

                    case "d":
                    case "dump":
                        doDumpAction(c);
                        break;
                    case "b":
                    case "break":
                        doBreakPointAction(c);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doDumpAction(String[] args) {
        if (args.length < 2) {
            printf("Usage: %s object|namespace|callstack\n", args[0]);
            return;
        }

        // TODO Make is beautiful.
        switch (args[1]) {
            case "object":
                if (args.length == 3) {
                    debugger.dumpObject(args[2]);
                    break;
                }
                printf("Usage: %s %s <object-name>\n", args[0], args[1]);
                break;
            case "namespace":
                if (args.length == 3) {
                    debugger.dumpNameSpace(args[2]);
                    break;
                }
                printf("Usage: %s %s <namespace-name>\n", args[0], args[1]);
                break;
            case "callstack":
                debugger.dumpCallStack();
                break;
        }
    }

    private void doBreakPointAction(String[] args) {
        if (args.length < 3) {
            printf("Usage: %s <file-name> <line> [line]...\n", args[0]);
            return;
        }

        String fileName = args[1];
        Set<Integer> breakPoints = debugger.getFileBreakPoints(fileName);
        for (int i = 2; i < args.length; ++i) {
            breakPoints.add(Integer.parseInt(args[i]));
        }
    }
}
