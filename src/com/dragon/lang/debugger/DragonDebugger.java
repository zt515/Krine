package com.dragon.lang.debugger;

import com.dragon.interpreter.DragonInterpreter;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * @author kiva
 * @date 2017/3/18
 */
public class DragonDebugger {
    private HashMap<String, Set<Integer>> breakPoints;
    private DragonInterpreter interpreter;

    public DragonDebugger(DragonInterpreter interpreter) {
        this.interpreter = interpreter;
        breakPoints = new HashMap<>(4);
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

    public void onBreakPointReached(BreakPoint breakPoint) {
        System.out.println("\n\n" + breakPoint);
        System.out.println("     " + breakPoint.getCode());
        System.out.print("Debugger> ");
        Scanner s = new Scanner(System.in);
        s.next();
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
