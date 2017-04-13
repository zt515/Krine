package krine.dynamic;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.ast.EvalError;
import com.krine.lang.ast.This;
import krine.core.KRuntimeException;

import java.util.HashMap;

/**
 * @author kiva
 * @date 2017/4/4
 */
@KrineAPI
@SuppressWarnings("unused")
public class Dynamic {
    public enum Accessibility {
        PUBLIC, PRIVATE, PROTECTED
    }

    private static final String[] ACCESSIBILITIES = new String[]{"public", "private", "protected"};
    
    public static void defineMethod(This aThis, Dynamic method) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);
        if (interpreter == null) {
            throw new KRuntimeException("Cannot get krine instance.");
        }
        
        try {
            interpreter.eval(method.generateCode());
        } catch (EvalError e) {
            throw new KRuntimeException("Error defining method " + method.getName());
        }
    }

    private Class<?> returnType;
    private HashMap<String, Class<?>> parameters;
    private String name;
    private String body;
    private Accessibility accessibility;

    private Dynamic() {
        parameters = new HashMap<>(2);
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public HashMap<String, Class<?>> getParameters() {
        return parameters;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public Accessibility getAccessibility() {
        return accessibility;
    }

    public String generateCode() {
        StringBuilder builder = new StringBuilder();

        // declaration
        builder.append(ACCESSIBILITIES[getAccessibility().ordinal()])
                .append(' ')
                .append(getName());

        // parameters
        builder.append('(');
        HashMap<String, Class<?>> params = getParameters();
        for (String paramName : params.keySet()) {
            builder.append(params.get(paramName).getName()).append(' ').append(paramName).append(',');
        }
        if (builder.charAt(builder.length() - 1) == ',') {
            builder.deleteCharAt(builder.length() - 1);
        }
        builder.append(')');

        // body
        builder.append('{')
                .append(getBody())
                .append('}');

        return builder.toString();
    }

    public static class MethodBuilder {
        private final Dynamic method;

        public MethodBuilder() {
            method = new Dynamic();
        }

        public MethodBuilder withName(String name) {
            method.name = name;
            return this;
        }

        public MethodBuilder withReturnType(Class<?> returnType) {
            method.returnType = returnType;
            return this;
        }

        public MethodBuilder addParameter(String name, Class<?> type) {
            method.parameters.put(name, type);
            return this;
        }

        public MethodBuilder withCode(String body) {
            method.body = body;
            return this;
        }

        public MethodBuilder withAccessibility(Accessibility accessibility) {
            method.accessibility = accessibility;
            return this;
        }

        public Dynamic build() {
            return method;
        }
    }
}
