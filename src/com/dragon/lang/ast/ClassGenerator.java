package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.InterpreterException;
import com.dragon.lang.UtilEvalException;
import com.dragon.lang.classgen.ClassGeneratorFactory;
import com.dragon.lang.classgen.IClassGenerator;
import com.dragon.lang.classpath.DragonClassManager;
import com.dragon.lang.classpath.GeneratedClass;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.reflect.ReflectException;
import com.dragon.lang.utils.CallStack;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


public final class ClassGenerator {

    /**
     * The name of the static field holding the reference to the dragon
     * static This (the callback namespace for static methods)
     */
    public static final String DRAGONSTATIC = "_dragonStatic";

    /**
     * The name of the instance field holding the reference to the dragon
     * instance This (the callback namespace for instance methods)
     */
    public static final String DRAGONTHIS = "_dragonThis";

    /**
     * The prefix for the name of the super delegate methods. e.g.
     * _dragonSuperfoo() is equivalent to super.foo()
     */
    public static final String DRAGONSUPER = "_dragonSuper";

    /**
     * The dragon static namespace variable name of the instance initializer
     */
    public static final String DRAGONINIT = "_dragonInstanceInitializer";

    /**
     * The dragon static namespace variable that holds the constructor methods
     */
    public static final String DRAGONCONSTRUCTORS = "_dragonConstructors";

    /**
     * The switch branch number for the default constructor.
     * The value -1 will cause the default branch to be taken.
     */
    public static final int DEFAULTCONSTRUCTOR = -1;

    public static final String OBJECT = "Ljava/lang/Object;";

    private static ClassGenerator cg;
    private static final String DEBUG_DIR = System.getProperty("dragon.debugClassssDir");

    private static final ThreadLocal<NameSpace> CONTEXT_NAMESPACE = new ThreadLocal<>();
    private static final ThreadLocal<DragonBasicInterpreter> CONTEXT_INTERPRETER = new ThreadLocal<>();


    /**
     * Register actual context, used by generated class constructor, which calls
     * {@link  #initInstance(GeneratedClass, String, Object[])}.
     */
    static void registerConstructorContext(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter) {
        if (callstack != null) {
            CONTEXT_NAMESPACE.set(callstack.top());
        } else {
            CONTEXT_NAMESPACE.remove();
        }
        if (dragonBasicInterpreter != null) {
            CONTEXT_INTERPRETER.set(dragonBasicInterpreter);
        } else {
            CONTEXT_INTERPRETER.remove();
        }
    }

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
    public Object invokeSuperclassMethod(DragonClassManager dcm, Object instance, String methodName, Object[] args) throws UtilEvalException, ReflectException, InvocationTargetException {
        // Delegate to the static method
        return invokeSuperclassMethodImpl(dcm, instance, methodName, args);
    }


    /**
     * Change the parent of the class instance namespace.
     * This is currently used for inner class support.
     * Note: This method will likely be removed in the future.
     */
    // This could be static
    public void setInstanceNameSpaceParent(Object instance, String className, NameSpace parent) {
        This instanceThis = getClassInstanceThis(instance, className);
        instanceThis.getNameSpace().setParent(parent);
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

        DragonClassManager dcm = dragonBasicInterpreter.getClassManager();
        // Race condition here...
        dcm.definingClass(fqClassName);

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

        // Generate class both for Java and Android
        IClassGenerator classGenerator = ClassGeneratorFactory.getClassGenerator();
        byte[] code = classGenerator.generateClass(modifiers, className, packageName, superClass, interfaces, variables, methods, classStaticNameSpace, isInterface);

        // if debug, write out the class file to debugClasses directory
        if (DEBUG_DIR != null) try {
            FileOutputStream out = new FileOutputStream(DEBUG_DIR + '/' + className + ".class");
            out.write(code);
            out.close();
        } catch (IOException e) {
            throw new IllegalStateException("cannot create file " + DEBUG_DIR + '/' + className + ".class", e);
        }

        // Define the new class in the classloader
        Class genClass = dcm.defineClass(fqClassName, code);

        // import the unq name into parent
        enclosingNameSpace.importClass(fqClassName.replace('$', '.'));

        try {
            classStaticNameSpace.setLocalVariable(DRAGONINIT, block, false/*strictJava*/);
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
            String dragonStaticFieldName = DRAGONSTATIC + className;
            try {
                LeftValue lhs = Reflect.getLHSStaticField(genClass, dragonStaticFieldName);
                lhs.assign(classStaticNameSpace.getThis(dragonBasicInterpreter), false/*strict*/);
            } catch (Exception e) {
                throw new InterpreterException("Error in class gen setup: " + e);
            }
        }

        dcm.doneDefiningClass(fqClassName);
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


    public static Object invokeSuperclassMethodImpl(DragonClassManager dcm, Object instance, String methodName, Object[] args) throws UtilEvalException, ReflectException, InvocationTargetException {
        String superName = DRAGONSUPER + methodName;

        // look for the specially named super delegate method
        Class clas = instance.getClass();
        Method superMethod = Reflect.resolveJavaMethod(dcm, clas, superName, Types.getTypes(args), false/*onlyStatic*/);
        if (superMethod != null) return Reflect.invokeMethod(superMethod, instance, args);

        // No super method, try to invoke regular method
        // could be a superfluous "super." which is legal.
        Class superClass = clas.getSuperclass();
        superMethod = Reflect.resolveExpectedJavaMethod(dcm, superClass, instance, methodName, args, false/*onlyStatic*/);
        return Reflect.invokeMethod(superMethod, instance, args);
    }

    public static void setLocalVariable(
            NameSpace ns, String name, Object value, boolean strictJava)
            throws UtilEvalException {
        ns.setLocalVariable(name, value, strictJava);
    }

    /**
     * Evaluate the arguments (if any) for the constructor specified by
     * the constructor index.  Return the ConstructorArgs object which
     * contains the actual arguments to the alternate constructor and also the
     * index of that constructor for the constructor switch.
     *
     * @param consArgs the arguments to the constructor.  These are necessary in
     *                 the evaluation of the alt constructor args.  e.g. Foo(a) { super(a); }
     * @return the ConstructorArgs object containing a constructor selector
     * and evaluated arguments for the alternate constructor
     */
    public static ConstructorArgs getConstructorArgs(String superClassName, This classStaticThis, Object[] consArgs, int index) {
        DelayedEvalDragonMethod[] constructors;
        try {
            constructors = (DelayedEvalDragonMethod[]) classStaticThis.getNameSpace().getVariable(DRAGONCONSTRUCTORS);
        } catch (Exception e) {
            throw new InterpreterException("unable to get instance initializer: " + e);
        }

        if (index == DEFAULTCONSTRUCTOR) // auto-gen default constructor
        {
            return ConstructorArgs.DEFAULT;
        } // use default super constructor

        DelayedEvalDragonMethod constructor = constructors[index];

        if (constructor.methodBody.jjtGetNumChildren() == 0) {
            return ConstructorArgs.DEFAULT;
        } // use default super constructor

        // Determine if the constructor calls this() or super()
        String altConstructor = null;
        DragonArguments argsNode = null;
        SimpleNode firstStatement = (SimpleNode) constructor.methodBody.jjtGetChild(0);
        if (firstStatement instanceof DragonPrimaryExpression) {
            firstStatement = (SimpleNode) firstStatement.jjtGetChild(0);
        }
        if (firstStatement instanceof DragonMethodInvocation) {
            DragonMethodInvocation methodNode = (DragonMethodInvocation) firstStatement;
            DragonAmbiguousName methodName = methodNode.getNameNode();
            if (methodName.text.equals("super") || methodName.text.equals("this")) {
                altConstructor = methodName.text;
                argsNode = methodNode.getArgsNode();
            }
        }

        if (altConstructor == null) {
            return ConstructorArgs.DEFAULT;
        } // use default super constructor

        // Make a tmp namespace to hold the original constructor args for
        // use in eval of the parameters node
        NameSpace consArgsNameSpace = new NameSpace(classStaticThis.getNameSpace(), "consArgs");
        String[] consArgNames = constructor.getParameterNames();
        Class[] consArgTypes = constructor.getParameterTypes();
        for (int i = 0; i < consArgs.length; i++) {
            try {
                consArgsNameSpace.setTypedVariable(consArgNames[i], consArgTypes[i], consArgs[i], null/*modifiers*/);
            } catch (UtilEvalException e) {
                throw new InterpreterException("err setting local cons arg:" + e);
            }
        }

        // evaluate the args

        CallStack callstack = new CallStack();
        callstack.push(consArgsNameSpace);
        Object[] args;
        DragonBasicInterpreter dragonBasicInterpreter = classStaticThis.declaringDragonBasicInterpreter;

        try {
            args = argsNode.getArguments(callstack, dragonBasicInterpreter);
        } catch (EvalError e) {
            throw new InterpreterException("Error evaluating constructor args: " + e);
        }

        Class[] argTypes = Types.getTypes(args);
        args = Primitive.unwrap(args);
        Class superClass = dragonBasicInterpreter.getClassManager().classForName(superClassName);
        if (superClass == null) {
            throw new InterpreterException("can't find superclass: " + superClassName);
        }
        Constructor[] superCons = superClass.getDeclaredConstructors();

        // find the matching super() constructor for the args
        if (altConstructor.equals("super")) {
            int i = Reflect.findMostSpecificConstructorIndex(argTypes, superCons);
            if (i == -1) {
                throw new InterpreterException("can't find constructor for args!");
            }
            return new ConstructorArgs(i, args);
        }

        // find the matching this() constructor for the args
        Class[][] candidates = new Class[constructors.length][];
        for (int i = 0; i < candidates.length; i++) {
            candidates[i] = constructors[i].getParameterTypes();
        }
        int i = Reflect.findMostSpecificSignature(argTypes, candidates);
        if (i == -1) {
            throw new InterpreterException("can't find constructor for args 2!");
        }
        // this() constructors come after super constructors in the table

        int selector = i + superCons.length;
        int ourSelector = index + superCons.length;

        // Are we choosing ourselves recursively through a this() reference?
        if (selector == ourSelector) {
            throw new InterpreterException("Recusive constructor call.");
        }

        return new ConstructorArgs(selector, args);
    }

    /**
     * Initialize an instance of the class.
     * This method is called from the generated class constructor to evaluate
     * the instance initializer and scripted constructor in the instance
     * namespace.
     */
    public static void initInstance(GeneratedClass instance, String className, Object[] args) {
        Class[] sig = Types.getTypes(args);
        CallStack callstack = new CallStack();
        DragonBasicInterpreter dragonBasicInterpreter;
        NameSpace instanceNameSpace;

        // check to see if the instance has already been initialized
        // (the case if using a this() alternate constructor)
        This instanceThis = getClassInstanceThis(instance, className);

        // XXX clean up this conditional
        if (instanceThis == null) {
            // Create the instance 'This' namespace, set it on the object
            // instance and invoke the instance initializer

            // Get the static This reference from the proto-instance
            This classStaticThis = getClassStaticThis(instance.getClass(), className);
            dragonBasicInterpreter = CONTEXT_INTERPRETER.get();
            if (dragonBasicInterpreter == null) {
                dragonBasicInterpreter = classStaticThis.declaringDragonBasicInterpreter;
            }


            // Get the instance initializer block from the static This
            DragonBlock instanceInitBlock;
            try {
                instanceInitBlock = (DragonBlock) classStaticThis.getNameSpace().getVariable(DRAGONINIT);
            } catch (Exception e) {
                throw new InterpreterException("unable to get instance initializer: " + e);
            }

            // Create the instance namespace
            if (CONTEXT_NAMESPACE.get() != null) {
                instanceNameSpace = classStaticThis.getNameSpace().copy();
                instanceNameSpace.setParent(CONTEXT_NAMESPACE.get());
            } else {
                instanceNameSpace = new NameSpace(classStaticThis.getNameSpace(), className); // todo: old code
            }
            instanceNameSpace.isClass = true;

            // Set the instance This reference on the instance
            instanceThis = instanceNameSpace.getThis(dragonBasicInterpreter);
            try {
                LeftValue lhs = Reflect.getLHSObjectField(instance, DRAGONTHIS + className);
                lhs.assign(instanceThis, false/*strict*/);
            } catch (Exception e) {
                throw new InterpreterException("Error in class gen setup: " + e);
            }

            // Give the instance space its object import
            instanceNameSpace.setClassInstance(instance);

            // should use try/finally here to pop ns
            callstack.push(instanceNameSpace);

            // evaluate the instance portion of the block in it
            try { // Evaluate the initializer block
                instanceInitBlock.evalBlock(callstack, dragonBasicInterpreter, true/*override*/, ClassGenerator.ClassNodeFilter.CLASSINSTANCE);
            } catch (Exception e) {
                throw new InterpreterException("Error in class initialization: " + e, e);
            }

            callstack.pop();

        } else {
            // The object instance has already been initialzed by another
            // constructor.  Fall through to invoke the constructor body below.
            dragonBasicInterpreter = instanceThis.declaringDragonBasicInterpreter;
            instanceNameSpace = instanceThis.getNameSpace();
        }

        // invoke the constructor method from the instanceThis

        String constructorName = getBaseName(className);
        try {
            // Find the constructor (now in the instance namespace)
            DragonMethod constructor = instanceNameSpace.getMethod(constructorName, sig, true/*declaredOnly*/);

            // if args, we must have constructor
            if (args.length > 0 && constructor == null) {
                throw new InterpreterException("Can't find constructor: " + className);
            }

            // Evaluate the constructor
            if (constructor != null) {
                constructor.invoke(args, dragonBasicInterpreter, callstack, null/*callerInfo*/, false/*overrideNameSpace*/);
            }
        } catch (Exception e) {
            if (e instanceof DragonTargetException) {
                e = (Exception) ((DragonTargetException) e).getTarget();
            }
            if (e instanceof InvocationTargetException) {
                e = (Exception) ((InvocationTargetException) e).getTargetException();
            }
            throw new InterpreterException("Error in class initialization: " + e, e);
        }
    }

    public static String[] getTypeDescriptors(Class[] paramClasses) {
        String[] sa = new String[paramClasses.length];
        for (int i = 0; i < sa.length; i++) {
            sa[i] = DragonType.getTypeDescriptor(paramClasses[i]);
        }
        return sa;
    }

    /**
     * Get the instance dragon namespace field from the object instance.
     *
     * @return the class instance This object or null if the object has not
     * been initialized.
     */
    static This getClassInstanceThis(Object instance, String className) {
        try {
            Object o = Reflect.getObjectFieldValue(instance, DRAGONTHIS + className);
            return (This) Primitive.unwrap(o); // unwrap Primitive.Null to null
        } catch (Exception e) {
            throw new InterpreterException("Generated class: Error getting This" + e);
        }
    }

    public static String getBaseName(String className) {
        int i = className.indexOf("$");
        if (i == -1) {
            return className;
        }

        return className.substring(i + 1);
    }

    /**
     * Get the static dragon namespace field from the class.
     *
     * @param className may be the name of clazz itself or a superclass of clazz.
     */
    private static This getClassStaticThis(Class clas, String className) {
        try {
            return (This) Reflect.getStaticFieldValue(clas, DRAGONSTATIC + className);
        } catch (Exception e) {
            throw new InterpreterException("Unable to get class static space: " + e);
        }
    }


    /**
     * A ConstructorArgs object holds evaluated arguments for a constructor
     * call as well as the index of a possible alternate selector to invoke.
     * This object is used by the constructor switch.
     */
    public static class ConstructorArgs {

        /**
         * A ConstructorArgs which calls the default constructor
         */
        public static final ConstructorArgs DEFAULT = new ConstructorArgs();

        public int selector = DEFAULTCONSTRUCTOR;
        Object[] args;
        int arg;


        /**
         * The index of the constructor to call.
         */

        ConstructorArgs() {
        }


        ConstructorArgs(int selector, Object[] args) {
            this.selector = selector;
            this.args = args;
        }


        Object next() {
            return args[arg++];
        }


        public boolean getBoolean() {
            return (Boolean) next();
        }


        public byte getByte() {
            return (Byte) next();
        }


        public char getChar() {
            return (Character) next();
        }


        public short getShort() {
            return (Short) next();
        }


        public int getInt() {
            return (Integer) next();
        }


        public long getLong() {
            return (Long) next();
        }


        public double getDouble() {
            return (Double) next();
        }


        public float getFloat() {
            return (Float) next();
        }


        public Object getObject() {
            return next();
        }
    }
}
