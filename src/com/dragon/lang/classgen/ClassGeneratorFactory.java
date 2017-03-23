package com.dragon.lang.classgen;
import com.dragon.lang.utils.*;

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

    
    private static IClassGenerator createClassGenerator() {
        if (Capabilities.isAndroid()) {
            return new DalvikClassGenerator();
        }
        return new DefaultJavaClassGenerator();
    }
}
