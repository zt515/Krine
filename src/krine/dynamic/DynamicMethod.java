package krine.dynamic;

import com.krine.api.annotations.KrineAPI;

import java.util.HashMap;

/**
 * @author kiva
 * @date 2017/4/4
 */
@KrineAPI
@SuppressWarnings("unused")
public class DynamicMethod {
    public enum Accessibility {
        PUBLIC, PRIVATE, PROTECTED
    }

    private static final String[] ACCESSIBILITIES = new String[]{"public", "private", "protected"};

    private Class<?> returnType;
    private HashMap<String, Class<?>> parameters;
    private String name;
    private String body;
    private Accessibility accessibility;

    private DynamicMethod() {
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

    public static class Builder {
        private final DynamicMethod method;

        public Builder() {
            method = new DynamicMethod();
        }

        public Builder withName(String name) {
            method.name = name;
            return this;
        }

        public Builder withReturnType(Class<?> returnType) {
            method.returnType = returnType;
            return this;
        }

        public Builder addParameter(String name, Class<?> type) {
            method.parameters.put(name, type);
            return this;
        }

        public Builder withCode(String body) {
            method.body = body;
            return this;
        }

        public Builder withAccessibility(Accessibility accessibility) {
            method.accessibility = accessibility;
            return this;
        }

        public DynamicMethod build() {
            return method;
        }
    }
}
