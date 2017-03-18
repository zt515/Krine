package com.dragon.lang.classgen;

import com.dragon.lang.ast.DelayedEvalDragonMethod;
import com.dragon.lang.ast.Modifiers;
import com.dragon.lang.ast.NameSpace;
import com.dragon.lang.ast.Variable;

/**
 * @author kiva
 * @date 2017/3/18
 */
public interface IClassGenerator {
    byte[] generateClass(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, DelayedEvalDragonMethod[] dragonMethods, NameSpace classStaticNameSpace, boolean isInterface);
}
