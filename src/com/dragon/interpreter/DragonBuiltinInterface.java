package com.dragon.interpreter;

import com.dragon.extension.DragonMethod;
import com.dragon.extension.IDragonLinkable;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class DragonBuiltinInterface implements IDragonLinkable {
    private DragonInterpreter interpreter;

    @DragonMethod
    public void println(Object o) {
        interpreter.println(o);
    }

    @DragonMethod
    public void exit(int retCode) {
        System.exit(retCode);
    }

    @DragonMethod
    public String getDragonVersion() {
        return DragonInterpreter.VERSION;
    }

    @Override
    public void bindInterpreter(DragonInterpreter interpreter) {
        this.interpreter = interpreter;
    }
}
