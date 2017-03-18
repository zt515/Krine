package com.dragon.extension;

import com.dragon.interpreter.DragonInterpreter;

/**
 * @author kiva
 * @date 2017/3/18
 */
public interface IDragonLinkable {
    void bindInterpreter(DragonInterpreter interpreter);
}
