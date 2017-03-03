package com.dragon.lang.ast;

import com.dragon.lang.*;
import com.dragon.lang.classpath.DragonClassManager;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.reflect.ReflectException;
import com.dragon.lang.utils.CallStack;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


public final class ClassGenerator {

    private static ClassGenerator cg;
    private static final String DEBUG_DIR = System.getProperty("dragon.debugClasses");


    public static ClassGenerator getClassGenerator() {
        if (cg == null) {
            cg = new ClassGenerator();
        }

        return cg;
    }


    /**
     * Parse the DragonBlock for the class definition and generate the class.
     */
    public Class generateClass(String name, Modifiers modifiers, Class[] interfaces, Class superClass, DragonBlock block, boolean isInterface, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        // Delegate to the static method
        return generateClassImpl(name, modifiers, interfaces, superClass, block, isInterface, callstack, dragonBasicInterpreter);
    }


    /**
     * Invoke a super.method() style superclass method on an object instance.
     * This is not a normal function of the Java reflection API and is
     * provided by generated class accessor methods.
     */
    public Object invokeSuperclassMethod(DragonClassManager bcm, Object instance, String methodName, Object[] args) throws UtilEvalException, ReflectException, InvocationTargetException {
        // Delegate to the static method
        return invokeSuperclassMethodImpl(bcm, instance, methodName, args);
    }


    /**
     * Change the parent of the class instance namespace.
     * This is currently used for inner class support.
     * Note: This method will likely be removed in the future.
     */
    // This could be static
    public void setInstanceNameSpaceParent(Object instance, String className, NameSpace parent) {
        This ithis = ClassGeneratorUtil.getClassInstanceThis(instance, className);
        ithis.getNameSpace().setParent(parent);
    }


    /**
     * Parse the DragonBlock for for the class definition and generate the class
     * using ClassGenerator.
     */
    public static Class generateClassImpl(String name, Modifiers modifiers, Class[] interfaces, Class superClass, DragonBlock block, boolean isInterface, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter) throws EvalError {
        NameSpace enclosingNameSpace = callstack.top();
        String packageName = enclosingNameSpace.getPackage();
        String className = enclosingNameSpace.isClass ? (enclosingNameSpace.getName() + "$" + name) : name;
        String fqClassName = packageName == null ? className : packageName + "." + className;

        DragonClassManager bcm = dragonBasicInterpreter.getClassManager();
        // Race condition here...
        bcm.definingClass(fqClassName);

        // Create the class static namespace
        NameSpace classStaticNameSpace = new NameSpace(enclosingNameSpace, className);
        classStaticNameSpace.isClass = true;

        callstack.push(classStaticNameSpace);

        // Evaluate any inner class class definitions in the block
        // effectively recursively call this method for contained classes first
        block.evalBlock(callstack, dragonBasicInterpreter, true/*override*/, ClassNodeFilter.CLASSCLASSES);

        // Generate the type for our class
        Variable[] variables = getDeclaredVariables(block, callstack, dragonBasicInterpreter, packageName);
        DelayedEvalDragonMethod[] methods = getDeclaredMethods(block, callstack, dragonBasicInterpreter, packageName);

        ClassGeneratorUtil classGenerator = new ClassGeneratorUtil(modifiers, className, packageName, superClass, interfaces, variables, methods, classStaticNameSpace, isInterface);
        byte[] code = classGenerator.generateClass();

        // if debug, write out the class file to debugClasses directory
        if (DEBUG_DIR != null) try {
            FileOutputStream out = new FileOutputStream(DEBUG_DIR + '/' + className + ".class");
            out.write(code);
            out.close();
        } catch (IOException e) {
            throw new IllegalStateException("cannot create file " + DEBUG_DIR + '/' + className + ".class", e);
        }

        // Define the new class in the classloader
        Class genClass = bcm.defineClass(fqClassName, code);

        // import the unq name into parent
        enclosingNameSpace.importClass(fqClassName.replace('$', '.'));

        try {
            classStaticNameSpace.setLocalVariable(ClassGeneratorUtil.DRAGONINIT, block, false/*strictJava*/);
        } catch (UtilEvalException e) {
            throw new InterpreterException("unable to init static: " + e);
        }

        // Give the static space its class static import
        // important to do this after all classes are defined
        classStaticNameSpace.setClassStatic(genClass);

        // evaluate the static portion of the block in the static space
        block.evalBlock(callstack, dragonBasicInterpreter, true/*override*/, ClassNodeFilter.CLASSSTATIC);

        callstack.pop();

        if (!genClass.isInterface()) {
            // Set the static dragon This callback
            String dragonStaticFieldName = ClassGeneratorUtil.DRAGONSTATIC + className;
            try {
                LeftValue lhs = Reflect.getLHSStaticField(genClass, dragonStaticFieldName);
                lhs.assign(classStaticNameSpace.getThis(dragonBasicInterpreter), false/*strict*/);
            } catch (Exception e) {
                throw new InterpreterException("Error in class gen setup: " + e);
            }
        }

        bcm.doneDefiningClass(fqClassName);
        return genClass;
    }


    static Variable[] getDeclaredVariables(DragonBlock body, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        List<Variable> vars = new ArrayList<Variable>();
        for (int child = 0; child < body.jjtGetNumChildren(); child++) {
            SimpleNode node = (SimpleNode) body.jjtGetChild(child);
            if (node instanceof DragonTypedVariableDeclaration) {
                DragonTypedVariableDeclaration tvd = (DragonTypedVariableDeclaration) node;
                Modifiers modifiers = tvd.modifiers;
                String type = tvd.getTypeDescriptor(callstack, dragonBasicInterpreter, defaultPackage);
                DragonVariableDeclarator[] vardec = tvd.getDeclarators();
                for (DragonVariableDeclarator aVardec : vardec) {
                    String name = aVardec.name;
                    try {
                        Variable var = new Variable(name, type, null/*value*/, modifiers);
                        vars.add(var);
                    } catch (UtilEvalException e) {
                        // value error shouldn't happen
                    }
                }
            }
        }

        return vars.toArray(new Variable[vars.size()]);
    }


    static DelayedEvalDragonMethod[] getDeclaredMethods(DragonBlock body, CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) throws EvalError {
        List<DelayedEvalDragonMethod> methods = new ArrayList<DelayedEvalDragonMethod>();
        for (int child = 0; child < body.jjtGetNumChildren(); child++) {
            SimpleNode node = (SimpleNode) body.jjtGetChild(child);
            if (node instanceof DragonMethodDeclaration) {
                DragonMethodDeclaration md = (DragonMethodDeclaration) node;
                md.insureNodesParsed();
                Modifiers modifiers = md.modifiers;
                String name = md.name;
                String returnType = md.getReturnTypeDescriptor(callstack, dragonBasicInterpreter, defaultPackage);
                DragonReturnType returnTypeNode = md.getReturnTypeNode();
                DragonFormalParameters paramTypesNode = md.paramsNode;
                String[] paramTypes = paramTypesNode.getTypeDescriptors(callstack, dragonBasicInterpreter, defaultPackage);

                DelayedEvalDragonMethod bm = new DelayedEvalDragonMethod(name, returnType, returnTypeNode, md.paramsNode.getParamNames(), paramTypes, paramTypesNode, md.blockNode, null/*declaringNameSpace*/, modifiers, callstack, dragonBasicInterpreter);

                methods.add(bm);
            }
        }
        return methods.toArray(new DelayedEvalDragonMethod[methods.size()]);
    }


    /**
     * A node filter that filters nodes for either a class body static
     * initializer or instance initializer.  In the static case only static
     * members are passed, etc.
     */
    static class ClassNodeFilter implements DragonBlock.NodeFilter {
        public static final int STATIC = 0, INSTANCE = 1, CLASSES = 2;

        public static ClassNodeFilter CLASSSTATIC = new ClassNodeFilter(STATIC);
        public static ClassNodeFilter CLASSINSTANCE = new ClassNodeFilter(INSTANCE);
        public static ClassNodeFilter CLASSCLASSES = new ClassNodeFilter(CLASSES);

        int context;


        private ClassNodeFilter(int context) {
            this.context = context;
        }


        public boolean isVisible(SimpleNode node) {
            if (context == CLASSES) return node instanceof DragonClassDeclaration;

            // Only show class decs in CLASSES
            if (node instanceof DragonClassDeclaration) return false;

            if (context == STATIC) return isStatic(node);

            if (context == INSTANCE) return !isStatic(node);

            // ALL
            return true;
        }


        boolean isStatic(SimpleNode node) {
            if (node instanceof DragonTypedVariableDeclaration)
                return ((DragonTypedVariableDeclaration) node).modifiers != null && ((DragonTypedVariableDeclaration) node).modifiers.hasModifier("static");

            if (node instanceof DragonMethodDeclaration)
                return ((DragonMethodDeclaration) node).modifiers != null && ((DragonMethodDeclaration) node).modifiers.hasModifier("static");

            // need to add static block here
            if (node instanceof DragonBlock) return false;

            return false;
        }
    }


    public static Object invokeSuperclassMethodImpl(DragonClassManager bcm, Object instance, String methodName, Object[] args) throws UtilEvalException, ReflectException, InvocationTargetException {
        String superName = ClassGeneratorUtil.DRAGONSUPER + methodName;

        // look for the specially named super delegate method
        Class clas = instance.getClass();
        Method superMethod = Reflect.resolveJavaMethod(bcm, clas, superName, Types.getTypes(args), false/*onlyStatic*/);
        if (superMethod != null) return Reflect.invokeMethod(superMethod, instance, args);

        // No super method, try to invoke regular method
        // could be a superfluous "super." which is legal.
        Class superClass = clas.getSuperclass();
        superMethod = Reflect.resolveExpectedJavaMethod(bcm, superClass, instance, methodName, args, false/*onlyStatic*/);
        return Reflect.invokeMethod(superMethod, instance, args);
    }

}
