package com.krine.lang.classgen;

import com.krine.lang.ast.KrineMethodDelayEvaluated;
import com.krine.lang.ast.Modifiers;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.Variable;

/**
 * @author kiva
 * @date 2017/3/18
 */
public interface IClassGenerator {
    byte[] generateClass(Modifiers classModifiers, String className,
                         String packageName, Class superClass, Class[] interfaces,
                         Variable[] vars, KrineMethodDelayEvaluated[] dragonMethods,
                         NameSpace classStaticNameSpace, boolean isInterface);
}
