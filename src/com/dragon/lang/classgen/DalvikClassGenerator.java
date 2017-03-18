package com.dragon.lang.classgen;

import com.dragon.lang.ast.DelayedEvalDragonMethod;
import com.dragon.lang.ast.Modifiers;
import com.dragon.lang.ast.NameSpace;
import com.dragon.lang.ast.Variable;

/**
 * @author kiva
 * @date 2017/3/18
 */
class DalvikClassGenerator extends DefaultJavaClassGenerator implements IClassGenerator {
    private static DexConverter DEX_CONVERTER = null;

    private static DexConverter getDexConverter() {
        if (DEX_CONVERTER == null) {
            synchronized (DalvikClassGenerator.class) {
                DEX_CONVERTER = new DexConverter();
            }
        }
        return DEX_CONVERTER;
    }

    @Override
    public byte[] generateClass(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, DelayedEvalDragonMethod[] dragonMethods, NameSpace classStaticNameSpace, boolean isInterface) {
        byte[] javaClass = super.generateClass(classModifiers, className, packageName, superClass, interfaces, vars, dragonMethods, classStaticNameSpace, isInterface);

        DexConverter dexConverter = getDexConverter();
        return dexConverter.convertJavaClass(packageName + "." + className, javaClass);
    }
}