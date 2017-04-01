package com.krine.extension;

import com.krine.interpreter.KrineInterpreter;

/**
 * @author kiva
 * @date 2017/3/18
 */
public interface IKrineLinkable {
    void bindInterpreter(KrineInterpreter interpreter);
}
