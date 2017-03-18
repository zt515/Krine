package com.dragon.lang.classgen;

/**
 * @author kiva
 * @date 2017/3/18
 */
public class ClassGeneratorFactory {
    private static IClassGenerator classGenerator;

    public static IClassGenerator getClassGenerator() {
        if (classGenerator == null) {
            synchronized (ClassGeneratorFactory.class) {
                classGenerator = createClassGenerator();
            }
        }
        return classGenerator;
    }

    private static boolean isAndroid() {
        // TODO Be Smarter
        try {
            Class.forName("android.app.Activity");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static IClassGenerator createClassGenerator() {
        if (isAndroid()) {
            return new DalvikClassGenerator();
        }
        return new DefaultJavaClassGenerator();
    }
}
