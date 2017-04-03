package com.krine.interpreter;

import com.krine.extension.IKrineLinkable;
import com.krine.extension.KrineMethod;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class KrineBuiltinInterface implements IKrineLinkable {
    private KrineInterpreter interpreter;

    @KrineMethod
    public void println(Object o) {
        interpreter.println(o);
    }

    @KrineMethod
    public void exit(int retCode) {
        System.exit(retCode);
    }

    @KrineMethod
    public String getKrineVersion() {
        return KrineInterpreter.VERSION;
    }

    @Override
    public void bindInterpreter(KrineInterpreter interpreter) {
        this.interpreter = interpreter;
    }
}
