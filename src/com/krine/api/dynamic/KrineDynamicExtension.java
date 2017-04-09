package com.krine.api.dynamic;

import com.krine.extension.IKrineLinkable;
import com.krine.extension.annotations.ExtensionConfig;
import com.krine.extension.annotations.KrineMethod;
import com.krine.interpreter.KrineInterpreter;
import com.krine.lang.ast.EvalError;
import krine.core.KRuntimeException;
import krine.dynamic.DynamicMethod;

/**
 * @author kiva
 * @date 2017/4/4
 */
@ExtensionConfig(requiredNameSpace = "krine.dynamic")
@SuppressWarnings("unused")
public class KrineDynamicExtension implements IKrineLinkable {
    private KrineInterpreter interpreter;

    @Override
    public void bindInterpreter(KrineInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    @KrineMethod
    public void applyMethod(DynamicMethod dynamicMethod) throws KRuntimeException {
        try {
            interpreter.eval(dynamicMethod.generateCode());
        } catch (EvalError evalError) {
            throw new KRuntimeException(evalError.getCause());
        }
    }
}
