package com.krine.lang.utils;

import com.krine.lang.ast.EvalError;
import com.krine.lang.ast.KrineTargetException;
import com.krine.lang.ast.SimpleNode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author kiva
 * @date 2017/4/3
 */
public class InvocationUtil {
    /**
     * Convert InvocationTargetException to Krine-processable Exception
     *
     * @param msg       Custom message
     * @param node      Node where exception occurs
     * @param callStack Program CallStack
     * @param e         InvocationTargetException
     * @return Converted Exception
     */
    public static KrineTargetException newTargetException(String msg, SimpleNode node,
                                                          CallStack callStack, InvocationTargetException e) {
        Throwable te = e.getTargetException();

        boolean isNative = true;
        if (te instanceof EvalError) {
            isNative = te instanceof KrineTargetException && ((KrineTargetException) te).exceptionInNative();
        }

        return new KrineTargetException(msg, te, node, callStack, isNative);
    }
}
