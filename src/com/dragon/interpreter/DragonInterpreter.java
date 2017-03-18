package com.dragon.interpreter;

import com.dragon.lang.*;
import com.dragon.lang.ast.DragonMethod;
import com.dragon.lang.ast.EvalError;
import com.dragon.lang.ast.NameSpace;
import com.dragon.lang.io.SystemIOBridge;
import com.dragon.extension.DragonNativeInterface;

import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Method;

/**
 * @author kiva
 * @date 2016/10/15
 */
public class DragonInterpreter extends com.dragon.lang.DragonBasicInterpreter {

    /**
     * Dragon Version
     */
    public static final String VERSION = "1.1";

    public DragonInterpreter(Reader in, PrintStream out, PrintStream err, NameSpace namespace, com.dragon.lang.DragonBasicInterpreter parent, String sourceFileInfo) {
        super(in, out, err, namespace, parent, sourceFileInfo);
        init();
    }

    public DragonInterpreter(Reader in, PrintStream out, PrintStream err, NameSpace namespace) {
        super(in, out, err, namespace);
        init();
    }

    public DragonInterpreter(Reader in, PrintStream out, PrintStream err) {
        super(in, out, err);
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
            linkNativeInterface(DragonNativeInterface.fromClass(DragonBuiltinInterface.class));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void linkNativeInterface(DragonNativeInterface nativeInterface) throws UtilEvalException {
        Object javaObject = nativeInterface.getObject();
        nativeInterface.getObject().bindInterpreter(this);

        for (Method m : nativeInterface.getMethods()) {
            DragonMethod method = new DragonMethod(m, javaObject);
            method.makePublic();
            getNameSpace().setMethod(method);
        }
    }

    public void linkNativeObject(String name, Object nativeObject) throws EvalError {
        set(name, nativeObject);
    }
}
