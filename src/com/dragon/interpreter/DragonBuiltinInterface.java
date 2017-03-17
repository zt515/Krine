package com.dragon.interpreter;

import com.dragon.extension.DragonNativeInterface;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class DragonBuiltinInterface {
    private DragonInterpreter interpreter;

    public DragonBuiltinInterface(DragonInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    @DragonNativeInterface
    public void print(Object o) {
        interpreter.print(o.toString());
    }

    @DragonNativeInterface
    public void exit(int retCode) {
        System.exit(retCode);
    }

    @DragonNativeInterface
    public void exit() {
        exit(0);
    }

    @DragonNativeInterface
    public int getDragonVersion() {
        return 19;
    }
}
