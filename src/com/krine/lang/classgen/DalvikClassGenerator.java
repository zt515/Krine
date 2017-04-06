package com.krine.lang.classgen;

import com.krine.lang.ast.KrineMethodDelayEvaluated;
import com.krine.lang.ast.Modifiers;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.Variable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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
    public byte[] generateClass(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, KrineMethodDelayEvaluated[] dragonMethods, NameSpace classStaticNameSpace, boolean isInterface) {
        byte[] javaClass = super.generateClass(classModifiers, className, packageName, superClass, interfaces, vars, dragonMethods, classStaticNameSpace, isInterface);

        DexConverter dexConverter = getDexConverter();
        byte[] out = dexConverter.convertJavaClass(packageName + "." + className, javaClass);

        if (System.getProperty("krineDexDebugDir") != null) {
            File dexFile = new File(System.getProperty("krineDexDebugDir"), className + ".dex");
            FileOutputStream os = null;
            
            try {
                os = new FileOutputStream(dexFile);
                os.write(out);
                os.flush();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException ignored) {
                        // ignored
                    }
                }
            }
        }

        return out;
    }
}
