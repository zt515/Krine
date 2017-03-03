package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.UtilEvalException;
import com.dragon.lang.classpath.ClassManagerImpl;

import java.io.PrintStream;
import java.io.StringReader;
import java.util.Map;

/**
 * With this class the script source is only parsed once and the resulting AST is used for
 * {@link #invoke(Map) every invocation}. This class is designed to be thread-safe.
 */
public class PreparsedScript {

    private final DragonMethod _method;
    private final DragonBasicInterpreter _Dragon_interpreter;


    public PreparsedScript(final String source) throws EvalError {
        this(source, getDefaultClassLoader());
    }


    private static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (final SecurityException e) {
            // ignore
        }
        if (cl == null) {
            cl = PreparsedScript.class.getClassLoader();
        }
        if (cl != null) {
            return cl;
        }
        return ClassLoader.getSystemClassLoader();
    }


    public PreparsedScript(final String source, final ClassLoader classLoader) throws EvalError {
        final ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassLoader(classLoader);
        final NameSpace nameSpace = new NameSpace(classManager, "global");
        _Dragon_interpreter = new DragonBasicInterpreter(new StringReader(""), System.out, System.err, false, nameSpace, null, null);
        try {
            final This callable = (This) _Dragon_interpreter.eval("__execute() { " + source + "\n" + "}\n" + "return this;");
            _method = callable.getNameSpace().getMethod("__execute", new Class[0], false);
        } catch (final UtilEvalException e) {
            throw new IllegalStateException(e);
        }
    }


    public Object invoke(final Map<String, ?> context) throws EvalError {
        final NameSpace nameSpace = new NameSpace(_Dragon_interpreter.getClassManager(), "BeanshellExecutable");
        nameSpace.setParent(_Dragon_interpreter.getNameSpace());
        final DragonMethod method = new DragonMethod(_method.getName(), _method.getReturnType(), _method.getParameterNames(), _method.getParameterTypes(), _method.methodBody, nameSpace, _method.getModifiers());
        for (final Map.Entry<String, ?> entry : context.entrySet()) {
            try {
                final Object value = entry.getValue();
                nameSpace.setVariable(entry.getKey(), value != null ? value : Primitive.NULL, false);
            } catch (final UtilEvalException e) {
                throw new EvalError("cannot set variable '" + entry.getKey() + '\'', null, null, e);
            }
        }
        final Object result = method.invoke(new Object[0], _Dragon_interpreter);
        if (result instanceof Primitive) {
            if (((Primitive) result).getType() == Void.TYPE) {
                return null;
            }
            return ((Primitive) result).getValue();
        }
        return result;
    }


    public void setOut(final PrintStream value) {
        _Dragon_interpreter.setOut(value);
    }


    public void setErr(final PrintStream value) {
        _Dragon_interpreter.setErr(value);
    }

}
