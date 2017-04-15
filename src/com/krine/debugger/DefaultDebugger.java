package com.krine.debugger;

import com.krine.debugger.ui.DebuggerUI;
import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.EvalError;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.This;
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
public class DefaultDebugger implements IDebugger {
    private HashMap<String, Set<Integer>> breakPoints;
    private DebuggerUI debuggerUI;

    private CallStack callStack;
    private KrineInterpreter interpreter;

    public DefaultDebugger(KrineInterpreter interpreter) {
        this.interpreter = interpreter;
        breakPoints = new HashMap<>(4);
        debuggerUI = new DebuggerUI(this, System.in);
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

    private void dumpObjectInternal(NameSpace ns, KrineInterpreter interpreter, String id) {
        try {
            Object object = ns.get(id, interpreter);
            dumpObjectInternal(id, object);

        } catch (Exception e) {
            debuggerUI.println("Error while dumping object \"" + id + "\", exception: " + e.getMessage());
        }
    }

    private void dumpObjectInternal(String id, Object object) {
        debuggerUI.printf("  Object: %s (Class: %s): %s\n",
                id, object.getClass().getCanonicalName(), object.toString());
    }

    @Override
    public void dumpObject(String objectName) {
        if (interpreter == null) {
            return;
        }

        NameSpace ns = interpreter.getNameSpace();
        dumpObjectInternal(ns, interpreter, objectName);
    }

    @Override
    public void dumpCallStack() {
        debuggerUI.println("CallStack Dump:");
        for (int i = 0; i < callStack.depth(); ++i) {
            debuggerUI.printf("  #%2d    %s\n", i, callStack.get(i).getName());
        }
    }

    @Override
    public void detach() {
        this.callStack = null;
        this.interpreter = null;
    }

    private void dumpNameSpaceInternal(NameSpace ns) {
        for (String id : ns.getAllNames()) {
            dumpObjectInternal(ns, interpreter, id);
        }
    }

    @Override
    public void dumpCurrentNameSpace() {
        if (interpreter == null) {
            return;
        }

        NameSpace ns = interpreter.getNameSpace();
        dumpNameSpaceInternal(ns);
    }

    @Override
    public void dumpNameSpace(String nsName) {
        if (interpreter == null) {
            return;
        }

        NameSpace ns = null;
        try {
            Object object = interpreter.get(nsName);
            if (object instanceof NameSpace) {
                ns = ((NameSpace) object);
            } else if (object instanceof This) {
                ns = ((This) object).getNameSpace();
            }
        } catch (EvalError evalError) {
            debuggerUI.println("Error while dumping NameSpace " + nsName);
        }

        if (ns != null) {
            dumpNameSpaceInternal(ns);
        }
    }

    @Override
    public String getName() {
        return "Krine Default Debugger";
    }

    @Override
    public void onProgramStarted(CallStack callStack) {
        this.callStack = callStack;
        debuggerUI.showUI(null);
    }

    @Override
    public void onProgramExited(Object returnValue) {
        debuggerUI.println("Program exited with value: ");
        dumpObjectInternal("<return value>", returnValue);

        // Do shutdown work
        debuggerUI.shutdown();
    }

    @Override
    public void onBreakPointReached(BreakPoint breakPoint) {
        if (interpreter == null) {
            return;
        }
        debuggerUI.showUI(breakPoint);
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
