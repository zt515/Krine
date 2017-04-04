package com.krine.interpreter;

import com.krine.dynamic.KrineDynamicInterface;
import com.krine.extension.IKrineLinkable;
import com.krine.extension.KrineExtension;
import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.ast.KrineMethod;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.io.SystemIOBridge;

import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author kiva
 * @date 2016/10/15
 */
public class KrineInterpreter extends KrineBasicInterpreter {

    /**
     * Krine Version
     */
    public static final String VERSION = "2.0";

    public KrineInterpreter(Reader in, PrintStream out, PrintStream err, NameSpace namespace, KrineBasicInterpreter parent, String sourceFileInfo) {
        super(in, out, err, namespace, parent, sourceFileInfo);
        init();
    }

    public KrineInterpreter(Reader in, PrintStream out, PrintStream err, NameSpace namespace) {
        super(in, out, err, namespace);
        init();
    }

    public KrineInterpreter(Reader in, PrintStream out, PrintStream err) {
        super(in, out, err);
        init();
    }

    public KrineInterpreter(SystemIOBridge console, NameSpace globalNameSpace) {
        super(console, globalNameSpace);
        init();
    }

    public KrineInterpreter(SystemIOBridge console) {
        super(console);
        init();
    }

    public KrineInterpreter() {
        super();
        init();
    }

    private void init() {
        try {
            linkNativeInterface(KrineExtension.fromClass(KrineBuiltinInterface.class));
            linkNativeInterface(KrineExtension.fromClass(KrineDynamicInterface.class));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void linkNativeInterface(KrineExtension nativeInterface)
            throws UtilEvalException, IllegalAccessException, InterpreterException, IllegalStateException {
        IKrineLinkable javaObject = nativeInterface.getObject();
        javaObject.bindInterpreter(this);

        NameSpace linkingNameSpace = getGlobalNameSpace();

//        String requiredNameSpace = nativeInterface.getRequiredNameSpace();
//        if (requiredNameSpace != null && !requiredNameSpace.isEmpty()
//                && !linkingNameSpace.getName().equals(requiredNameSpace)) {
//            Object check = getGlobalNameSpace().get(requiredNameSpace, this);
//            if (check == null) {
//                linkingNameSpace = new NameSpace(getNameSpace(), requiredNameSpace);
//            } else if (check instanceof NameSpace) {
//                linkingNameSpace = ((NameSpace) check);
//            } else {
//                throw new IllegalStateException("NameSpace cannot be initialized.");
//            }
//        }

        for (Method m : nativeInterface.getMethods()) {
            KrineMethod method = new KrineMethod(m, javaObject);
            method.makePublic();

            linkingNameSpace.setMethod(method);
        }

        for (Field f : nativeInterface.getFields()) {
            linkingNameSpace.setVariable(f.getName(), f.get(javaObject), false);
        }
    }

    public void linkNativeObject(String name, Object nativeObject) throws InterpreterException {
        setUnchecked(name, nativeObject);
    }
}
