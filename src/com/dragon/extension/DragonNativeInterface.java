package com.dragon.extension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class DragonNativeInterface {
    private List<Method> methods;
    private List<Field> fields;

    private IDragonLinkable object;

    private DragonNativeInterface(List<Method> methods, List<Field> fields, IDragonLinkable object) {
        this.fields = fields;
        this.methods = methods;
        this.object = object;
    }

    public static DragonNativeInterface fromClass(Class<? extends IDragonLinkable> clazz)
            throws IllegalAccessException, InstantiationException {
        IDragonLinkable object = clazz.newInstance();

        List<Method> methods = new ArrayList<>(4);
        List<Field> fields = new ArrayList<>(4);

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotation(DragonMethod.class) != null) {
                methods.add(m);
            }
        }

        for (Field f : clazz.getDeclaredFields()) {
            if (f.getAnnotation(DragonVariable.class) != null) {
                fields.add(f);
            }
        }

        return new DragonNativeInterface(methods, fields, object);
    }

    public List<Method> getMethods() {
        return methods;
    }

    public List<Field> getFields() {
        return fields;
    }

    public IDragonLinkable getObject() {
        return object;
    }
}
