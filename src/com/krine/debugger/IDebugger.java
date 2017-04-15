package com.krine.debugger;

import com.krine.lang.utils.CallStack;

import java.util.Set;

/**
 * @author kiva
 * @date 2017/4/16
 */
public interface IDebugger {
    String getName();

    void onProgramStarted(CallStack callStack);
    void onProgramExited(Object returnValue);
    void onBreakPointReached(BreakPoint breakPoint);

    void dumpObject(String objectName);
    void dumpNameSpace(String nsName);
    void dumpCurrentNameSpace();
    void dumpCallStack();
    void detach();

    Set<Integer> getFileBreakPoints(String sourceFile);
}
