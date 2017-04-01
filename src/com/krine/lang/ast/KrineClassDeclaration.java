package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

/**
 */
class KrineClassDeclaration extends SimpleNode {
    /**
     * The class instance initializer method name.
     * A KrineMethod by this name is installed by the class delcaration into
     * the static class body namespace.
     * It is called once to initialize the static members of the class space
     * and each time an instances is created to initialize the instance
     * members.
     */
    static final String CLASSINITNAME = "_krineClassInit";

    String name;
    Modifiers modifiers;
    int numInterfaces;
    boolean extend;
    boolean isInterface;
    private Class<?> generatedClass;

    KrineClassDeclaration(int id) {
        super(id);
    }

    /**
     */
    public synchronized Object eval(final CallStack callstack, final KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        waitForDebugger();

        if (generatedClass == null) {
            generatedClass = generateClass(callstack, krineBasicInterpreter);
        }
        return generatedClass;
    }


    private Class<?> generateClass(final CallStack callstack, final KrineBasicInterpreter krineBasicInterpreter) throws EvalError {
        int child = 0;

        // resolve superclass if any
        Class superClass = null;
        if (extend) {
            KrineAmbiguousName superNode = (KrineAmbiguousName) jjtGetChild(child++);
            superClass = superNode.toClass(callstack, krineBasicInterpreter);
        }

        // Get interfaces
        Class[] interfaces = new Class[numInterfaces];
        for (int i = 0; i < numInterfaces; i++) {
            KrineAmbiguousName node = (KrineAmbiguousName) jjtGetChild(child++);
            interfaces[i] = node.toClass(callstack, krineBasicInterpreter);
            if (!interfaces[i].isInterface())
                throw new EvalError(
                        "Type: " + node.text + " is not an interface!",
                        this, callstack);
        }

        KrineBlock block;
        // Get the class body KrineBlock
        if (child < jjtGetNumChildren())
            block = (KrineBlock) jjtGetChild(child);
        else
            block = new KrineBlock(ParserTreeConstants.JJTBLOCK);

        return ClassGenerator.getClassGenerator().generateClass(
                name, modifiers, interfaces, superClass, block, isInterface,
                callstack, krineBasicInterpreter);
    }


    public String toString() {
        return "ClassDeclaration: " + name;
    }
}
