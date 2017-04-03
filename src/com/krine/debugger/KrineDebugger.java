package com.krine.debugger;

import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.utils.CallStack;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * @author kiva
 * @date 2017/3/18
 */
public class KrineDebugger {
    private HashMap<String, Set<Integer>> breakPoints;
    private CallStack callstack;
    private KrineInterpreter interpreter;

    public KrineDebugger(KrineInterpreter interpreter) {
        this.interpreter = interpreter;
        breakPoints = new HashMap<>(4);
    }

    public void bindCallStack(CallStack callstack) {
        this.callstack = callstack;
    }

    public void startDebugging() {
        interpreter.setDebugger(this);
    }

    public BreakPointAdder addBreakPoint(String file) {
        file = absolutePath(file);

        Set<Integer> set = breakPoints.get(file);
        if (set == null) {
            set = new HashSet<>(4);
            breakPoints.put(file, set);
        }

        return new BreakPointAdder(set);
    }

    private String absolutePath(String file) {
        return new File(file).getAbsolutePath();
    }

    public Set<Integer> getFileBreakPoints(String file) {
        return breakPoints.get(absolutePath(file));
    }

    public void dumpCallingStack() {
        System.err.println("CallStack Dump:");
        for (int i = 0; i < callstack.depth(); ++i) {
            System.err.printf("  #%2d    %s\n", i, callstack.get(i).getName());
        }
    }

    public void dumpCurrentNameSpace() {
        if (interpreter == null) {
            return;
        }

        NameSpace ns = interpreter.getNameSpace();
        for (String id : ns.getAllNames()) {
            dumpObjectInternal(ns, interpreter, id);
        }
    }

    private void dumpObjectInternal(NameSpace ns, KrineInterpreter interpreter, String id) {
        try {
            Object object = ns.get(id, interpreter);
            System.err.printf("  Object: %s (Class: %s): %s\n",
                    id, object.getClass().getCanonicalName(), object.toString());

        } catch (Exception e) {
            System.err.println("Error while dumping object \"" + id + "\", exception: " + e.getMessage());
        }
    }

    public void dumpObject(String id) {
        if (interpreter == null) {
            return;
        }

        NameSpace ns = interpreter.getNameSpace();
        dumpObjectInternal(ns, interpreter, id);
    }

    public void onBreakPointReached(BreakPoint breakPoint) {
        if (interpreter == null) {
            return;
        }

        System.err.println(breakPoint);
        System.err.println("     " + breakPoint.getCode());
        Scanner reader = new Scanner(System.in);

        String s;
        for (; ; ) {
            System.err.print("ddb> ");
            if ((s = reader.next()) == null) {
                break;
            }

            String[] c = s.split(" ");

            if (c[0].equals("n") || c[0].equals("next")) {
                break;
            }

            if (c[0].equals("detach")) {
                this.interpreter = null;
                this.callstack = null;
                break;
            }


            switch (s) {
                case "dcs":
                    dumpCallingStack();
                    break;
                case "dns":
                    dumpCurrentNameSpace();
                    break;

            }
        }
        reader.close();
    }

    public static class BreakPointAdder {
        private Set<Integer> breakPointSet;

        BreakPointAdder(Set<Integer> breakPointSet) {
            this.breakPointSet = breakPointSet;
        }

        public BreakPointAdder add(int line) {
            breakPointSet.add(line);
            return this;
        }
    }
}
