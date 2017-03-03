package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

/**
 */
class DragonClassDeclaration extends SimpleNode {
    /**
     * The class instance initializer method name.
     * A DragonMethod by this name is installed by the class delcaration into
     * the static class body namespace.
     * It is called once to initialize the static members of the class space
     * and each time an instances is created to initialize the instance
     * members.
     */
    static final String CLASSINITNAME = "_dragonClassInit";

    String name;
    Modifiers modifiers;
    int numInterfaces;
    boolean extend;
    boolean isInterface;
    private Class<?> generatedClass;

    DragonClassDeclaration(int id) {
        super(id);
    }

    /**
     */
    public synchronized Object eval(final CallStack callstack, final DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        if (generatedClass == null) {
            generatedClass = generateClass(callstack, dragonBasicInterpreter);
        }
        return generatedClass;
    }


    private Class<?> generateClass(final CallStack callstack, final DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        int child = 0;

        // resolve superclass if any
        Class superClass = null;
        if (extend) {
            DragonAmbiguousName superNode = (DragonAmbiguousName) jjtGetChild(child++);
            superClass = superNode.toClass(callstack, dragonBasicInterpreter);
        }

        // Get interfaces
        Class[] interfaces = new Class[numInterfaces];
        for (int i = 0; i < numInterfaces; i++) {
            DragonAmbiguousName node = (DragonAmbiguousName) jjtGetChild(child++);
            interfaces[i] = node.toClass(callstack, dragonBasicInterpreter);
            if (!interfaces[i].isInterface())
                throw new EvalError(
                        "Type: " + node.text + " is not an interface!",
                        this, callstack);
        }

        DragonBlock block;
        // Get the class body DragonBlock
        if (child < jjtGetNumChildren())
            block = (DragonBlock) jjtGetChild(child);
        else
            block = new DragonBlock(ParserTreeConstants.JJTBLOCK);

        return ClassGenerator.getClassGenerator().generateClass(
                name, modifiers, interfaces, superClass, block, isInterface,
                callstack, dragonBasicInterpreter);
    }


    public String toString() {
        return "ClassDeclaration: " + name;
    }
}
