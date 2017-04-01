package com.krine.extension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class KrineNativeInterface {
    private List<Method> methods;
    private List<Field> fields;

    private IKrineLinkable object;

    private KrineNativeInterface(List<Method> methods, List<Field> fields, IKrineLinkable object) {
        this.fields = fields;
        this.methods = methods;
        this.object = object;
    }

    public static KrineNativeInterface fromClass(Class<? extends IKrineLinkable> clazz)
            throws IllegalAccessException, InstantiationException {
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

        return new KrineNativeInterface(methods, fields, object);
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
}
