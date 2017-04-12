package com.krine.api.core;

import com.krine.extension.IKrineLinkable;
import com.krine.extension.annotations.ExtensionConfig;
import com.krine.extension.annotations.KrineMethod;
import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.Version;

/**
 * @author kiva
 * @date 2017/2/24
 */
@ExtensionConfig
@SuppressWarnings("unused")
public class KrineCoreExtension implements IKrineLinkable {
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
        return Version.current();
    }

    @Override
    public void bindInterpreter(KrineInterpreter interpreter) {
        this.interpreter = interpreter;
    }
}
