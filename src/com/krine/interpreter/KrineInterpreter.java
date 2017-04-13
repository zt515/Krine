package com.krine.interpreter;

import com.krine.api.core.KrineCoreExtension;
import com.krine.extension.IKrineLinkable;
import com.krine.extension.KrineExtension;
import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.ast.KrineMethod;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.io.SystemIOBridge;
import com.krine.lang.utils.StringUtil;

import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author kiva
 * @date 2016/10/15
 */
public class KrineInterpreter extends KrineBasicInterpreter {

    public KrineInterpreter(Reader in, PrintStream out, PrintStream err, NameSpace namespace, KrineBasicInterpreter parent, String sourceFileInfo) {
        super(in, out, err, namespace, parent);
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
            linkNativeInterface(KrineExtension.fromClass(KrineCoreExtension.class));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void linkNativeInterface(KrineExtension nativeInterface)
            throws UtilEvalException, IllegalAccessException, InterpreterException, IllegalStateException {
        IKrineLinkable javaObject = nativeInterface.getObject();
        javaObject.bindInterpreter(this);

        NameSpace globalNameSpace = getGlobalNameSpace();
        NameSpace linkingNameSpace = globalNameSpace;

        String requiredNameSpace = nativeInterface.getRequiredNameSpace();
        if (!StringUtil.isEmpty(requiredNameSpace)) {
            if (globalNameSpace.getName().equals(requiredNameSpace)) {
                linkingNameSpace = globalNameSpace;
            } else {
                Object object = globalNameSpace.getVariable(requiredNameSpace, true);
                if (object != null && object instanceof NameSpace) {
                    linkingNameSpace = ((NameSpace) object);
                } else {
                    linkingNameSpace = new NameSpace(linkingNameSpace, "KrineExt_" + requiredNameSpace);
                    setUnchecked(requiredNameSpace, linkingNameSpace.getThis(this));
                }
            }
        }

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
