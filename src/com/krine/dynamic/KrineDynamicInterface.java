package com.krine.dynamic;

import com.krine.extension.IKrineLinkable;
import com.krine.extension.annotations.ExtensionConfig;
import com.krine.extension.annotations.KrineMethod;
import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.EvalError;
import krine.dynamic.DynamicMethod;

/**
 * @author kiva
 * @date 2017/4/4
 */
@ExtensionConfig(requiredNameSpace = "krine.dynamic")
public class KrineDynamicInterface implements IKrineLinkable {
    private KrineInterpreter interpreter;

    @Override
    public void bindInterpreter(KrineInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    @KrineMethod
    public void loadDynamicMethod(DynamicMethod dynamicMethod) throws EvalError {
        interpreter.eval(dynamicMethod.generateCode());
    }
}
