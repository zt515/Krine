package com.dragon.interpreter;

import com.dragon.lang.*;
import com.dragon.lang.ast.DragonMethod;
import com.dragon.lang.ast.EvalError;
import com.dragon.lang.ast.NameSpace;
import com.dragon.lang.io.SystemIOBridge;
import com.dragon.extension.DragonNativeMethod;

import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Method;

/**
 * Created by kiva on 2016/10/15.
 */
public class DragonInterpreter extends com.dragon.lang.DragonBasicInterpreter {

    public DragonInterpreter(Reader in, PrintStream out, PrintStream err, boolean interactive, NameSpace namespace, com.dragon.lang.DragonBasicInterpreter parent, String sourceFileInfo) {
        super(in, out, err, interactive, namespace, parent, sourceFileInfo);
        init();
    }

    public DragonInterpreter(Reader in, PrintStream out, PrintStream err, boolean interactive, NameSpace namespace) {
        super(in, out, err, interactive, namespace);
        init();
    }

    public DragonInterpreter(Reader in, PrintStream out, PrintStream err, boolean interactive) {
        super(in, out, err, interactive);
        init();
    }

    public DragonInterpreter(SystemIOBridge console, NameSpace globalNameSpace) {
        super(console, globalNameSpace);
        init();
    }

    public DragonInterpreter(SystemIOBridge console) {
        super(console);
        init();
    }

    public DragonInterpreter() {
        super();
        init();
    }

    private void init() {
        try {
            linkNativeMethod(DragonNativeMethod.wrapJavaMethod(new DragonBuiltinInterface()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void linkNativeMethod(DragonNativeMethod nativeMethodHolder) throws UtilEvalException {
        Object javaObject = nativeMethodHolder.getObject();
        for (Method m : nativeMethodHolder.getMethods()) {
            DragonMethod method = new DragonMethod(m, javaObject);
            method.makePublic();
            getNameSpace().setMethod(method);
        }
    }

    public void linkNativeObject(String name, Object nativeObject) throws EvalError {
        set(name, nativeObject);
    }
}
