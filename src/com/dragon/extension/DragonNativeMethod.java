package com.dragon.extension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kiva
 * @date 2017/2/24
 */
public class DragonNativeMethod {
    private List<Method> methods;
    private Object object;

    private DragonNativeMethod(List<Method> methods, Object object) {
        this.methods = methods;
        this.object = object;
    }

    public List<Method> getMethods() {
        return methods;
    }

    public Object getObject() {
        return object;
    }

    public static DragonNativeMethod wrapJavaMethod(Object object) {
        Class clazz = object.getClass();

        List<Method> methods = new ArrayList<>(1);

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotation(DragonNativeInterface.class) != null) {
                methods.add(m);
            }
        }

        return new DragonNativeMethod(methods, object);
    }
}
