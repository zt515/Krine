package com.dragon.interpreter;

import com.dragon.extension.DragonNativeInterface;

/**
 * Created by kiva on 2017/2/24.
 */
public class DragonBuiltinInterface {

    @DragonNativeInterface
    public void print(Object o) {
        System.out.println(o.toString());
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
