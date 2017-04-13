package com.krine.extension;

import com.krine.extension.annotations.ExtensionConfig;
import com.krine.extension.annotations.KrineMethod;
import com.krine.extension.annotations.KrineVariable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class KrineExtension {
    private List<Method> methods;
    private List<Field> fields;
    private IKrineLinkable object;
    private String requiredNameSpace;

    private KrineExtension(List<Method> methods, List<Field> fields, String requiredNameSpace, IKrineLinkable object) {
        this.fields = fields;
        this.methods = methods;
        this.requiredNameSpace = requiredNameSpace;
        this.object = object;
    }

    public static KrineExtension fromClass(Class<? extends IKrineLinkable> clazz)
            throws IllegalAccessException, InstantiationException {
        ExtensionConfig config = clazz.getAnnotation(ExtensionConfig.class);
        if (config == null) {
            throw new IllegalAccessException("No module config for " + clazz.getName() + " found.");
        }

        IKrineLinkable object = clazz.newInstance();

        List<Method> methods = new ArrayList<>(4);
        List<Field> fields = new ArrayList<>(4);

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotation(KrineMethod.class) != null) {
                methods.add(m);
            }
        }

        for (Field f : clazz.getDeclaredFields()) {
            if (f.getAnnotation(KrineVariable.class) != null) {
                fields.add(f);
            }
        }

        return new KrineExtension(methods, fields, config.requiredNameSpace(), object);
    }

    public List<Method> getMethods() {
        return methods;
    }

    public List<Field> getFields() {
        return fields;
    }

    public IKrineLinkable getObject() {
        return object;
    }

    public String getRequiredNameSpace() {
        return requiredNameSpace;
    }
}
