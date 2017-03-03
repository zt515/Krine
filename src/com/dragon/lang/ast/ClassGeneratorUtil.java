package com.dragon.lang.ast;

import com.dragon.lang.*;
import com.dragon.lang.asm.*;
import com.dragon.lang.classpath.GeneratedClass;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.utils.Capabilities;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ClassGeneratorUtil utilizes the ASM (www.objectweb.org) bytecode generator
 * by Eric Bruneton in order to generate class "stubs" for Dragon at
 * runtime.
 * <p/>
 * <p/>
 * Stub classes contain all of the fields of a Dragon scripted class
 * as well as two "callback" references to Dragon namespaces: one for
 * static methods and one for instance methods.  Methods of the class are
 * delegators which invoke corresponding methods on either the static or
 * instance dragon object and then unpack and return the results.  The static
 * namespace utilizes a static import to delegate variable access to the
 * class' static fields.  The instance namespace utilizes a dynamic import
 * (i.e. mixin) to delegate variable access to the class' instance variables.
 * <p/>
 * <p/>
 * Constructors for the class delegate to the static initInstance() method of
 * ClassGeneratorUtil to initialize new instances of the object.  initInstance()
 * invokes the instance intializer code (init vars and instance blocks) and
 * then delegates to the corresponding scripted constructor method in the
 * instance namespace.  Constructors contain special switch logic which allows
 * the Dragon to control the calling of alternate constructors (this() or
 * super() references) at runtime.
 * <p/>
 * <p/>
 * Specially named superclass delegator methods are also generated in order to
 * allow Dragon to access overridden methods of the superclass (which
 * reflection does not normally allow).
 * <p/>
 *
 * @author Pat Niemeyer
 */
/*
    Notes:
	It would not be hard to eliminate the use of org.objectweb.asm.Type from
	this class, making the distribution a tiny bit smaller.
*/
public class ClassGeneratorUtil implements Constants {

    /**
     * The name of the static field holding the reference to the dragon
     * static This (the callback namespace for static methods)
     */
    static final String DRAGONSTATIC = "_dragonStatic";

    /**
     * The name of the instance field holding the reference to the dragon
     * instance This (the callback namespace for instance methods)
     */
    private static final String DRAGONTHIS = "_dragonThis";

    /**
     * The prefix for the name of the super delegate methods. e.g.
     * _dragonSuperfoo() is equivalent to super.foo()
     */
    static final String DRAGONSUPER = "_dragonSuper";

    /**
     * The dragon static namespace variable name of the instance initializer
     */
    static final String DRAGONINIT = "_dragonInstanceInitializer";

    /**
     * The dragon static namespace variable that holds the constructor methods
     */
    private static final String DRAGONCONSTRUCTORS = "_dragonConstructors";

    /**
     * The switch branch number for the default constructor.
     * The value -1 will cause the default branch to be taken.
     */
    private static final int DEFAULTCONSTRUCTOR = -1;

    private static final String OBJECT = "Ljava/lang/Object;";

    private final String className;
    /**
     * fully qualified class name (with package) e.g. foo/bar/Blah
     */
    private final String fqClassName;
    private final Class superClass;
    private final String superClassName;
    private final Class[] interfaces;
    private final Variable[] vars;
    private final Constructor[] superConstructors;
    private final DelayedEvalDragonMethod[] constructors;
    private final DelayedEvalDragonMethod[] methods;
    private final NameSpace classStaticNameSpace;
    private final Modifiers classModifiers;
    private boolean isInterface;


    /**
     * @param packageName e.g. "com.foo.bar"
     */
    public ClassGeneratorUtil(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, DelayedEvalDragonMethod[] dragonMethods, NameSpace classStaticNameSpace, boolean isInterface) {
        this.classModifiers = classModifiers;
        this.className = className;
        if (packageName != null) {
            this.fqClassName = packageName.replace('.', '/') + "/" + className;
        } else {
            this.fqClassName = className;
        }
        if (superClass == null) {
            superClass = Object.class;
        }
        this.superClass = superClass;
        this.superClassName = Type.getInternalName(superClass);
        if (interfaces == null) {
            interfaces = new Class[0];
        }
        this.interfaces = interfaces;
        this.vars = vars;
        this.classStaticNameSpace = classStaticNameSpace;
        this.superConstructors = superClass.getDeclaredConstructors();

        // Split the methods into constructors and regular method lists
        List consl = new ArrayList();
        List methodsl = new ArrayList();
        String classBaseName = getBaseName(className); // for inner classes
        for (DelayedEvalDragonMethod dragonMethod : dragonMethods) {
            if (dragonMethod.getName().equals(classBaseName)) {
                consl.add(dragonMethod);
                if ((packageName == null) && !Capabilities.haveAccessibility()) {
                    dragonMethod.makePublic();
                }
            } else {
                methodsl.add(dragonMethod);
            }
        }

        this.constructors = (DelayedEvalDragonMethod[]) consl.toArray(new DelayedEvalDragonMethod[consl.size()]);
        this.methods = (DelayedEvalDragonMethod[]) methodsl.toArray(new DelayedEvalDragonMethod[methodsl.size()]);

        try {
            classStaticNameSpace.setLocalVariable(DRAGONCONSTRUCTORS, constructors, false/*strict*/);
        } catch (UtilEvalException e) {
            throw new InterpreterException("can't set cons var");
        }

        this.isInterface = isInterface;
    }


    /**
     * Generate the class bytecode for this class.
     */
    public byte[] generateClass() {
        // Force the class public for now...
        int classMods = getASMModifiers(classModifiers) | ACC_PUBLIC;
        if (isInterface) {
            classMods |= ACC_INTERFACE;
        }

        String[] interfaceNames = new String[interfaces.length + (isInterface ? 0 : 1)]; // one more interface for instance init callback
        for (int i = 0; i < interfaces.length; i++) {
            interfaceNames[i] = Type.getInternalName(interfaces[i]);
        }
        if (!isInterface) {
            interfaceNames[interfaces.length] = Type.getInternalName(GeneratedClass.class);
        }

        String sourceFile = "Dragon Generated via ASM (www.objectweb.org)";
        ClassWriter cw = new ClassWriter(false);
        cw.visit(classMods, fqClassName, superClassName, interfaceNames, sourceFile);

        if (!isInterface) {
            // Generate the dragon instance 'This' reference holder field
            generateField(DRAGONTHIS + className, "Lcom/dragon/lang/ast/This;", ACC_PUBLIC, cw);

            // Generate the static dragon static reference holder field
            generateField(DRAGONSTATIC + className, "Lcom/dragon/lang/ast/This;", ACC_PUBLIC + ACC_STATIC, cw);
        }

        // Generate the fields
        for (Variable var : vars) {
            String type = var.getTypeDescriptor();

            // Don't generate private or loosely typed fields
            // Note: loose types aren't currently parsed anyway...
            if (var.hasModifier("private") || type == null) {
                continue;
            }

            int modifiers;
            if (isInterface) {
                modifiers = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;
            } else {
                modifiers = getASMModifiers(var.getModifiers());
            }

            generateField(var.getName(), type, modifiers, cw);
        }

        // Generate the constructors
        boolean hasConstructor = false;
        for (int i = 0; i < constructors.length; i++) {
            // Don't generate private constructors
            if (constructors[i].hasModifier("private")) {
                continue;
            }

            int modifiers = getASMModifiers(constructors[i].getModifiers());
            generateConstructor(i, constructors[i].getParamTypeDescriptors(), modifiers, cw);
            hasConstructor = true;
        }

        // If no other constructors, generate a default constructor
        if (!isInterface && !hasConstructor) {
            generateConstructor(DEFAULTCONSTRUCTOR/*index*/, new String[0], ACC_PUBLIC, cw);
        }

        // Generate the delegate methods
        for (DelayedEvalDragonMethod method : methods) {
            String returnType = method.getReturnTypeDescriptor();

            // Don't generate private /*or loosely return typed */ methods
            if (method.hasModifier("private") /*|| returnType == null*/) {
                continue;
            }

            int modifiers = getASMModifiers(method.getModifiers());
            if (isInterface) {
                modifiers |= (ACC_PUBLIC | ACC_ABSTRACT);
            }

            generateMethod(className, fqClassName, method.getName(), returnType, method.getParamTypeDescriptors(), modifiers, cw);

            boolean isStatic = (modifiers & ACC_STATIC) > 0;
            boolean overridden = classContainsMethod(superClass, method.getName(), method.getParamTypeDescriptors());
            if (!isStatic && overridden) {
                generateSuperDelegateMethod(superClassName, method.getName(), returnType, method.getParamTypeDescriptors(), modifiers, cw);
            }
        }

        return cw.toByteArray();
    }


    /**
     * Translate dragon.Modifiers into ASM modifier bitflags.
     */
    private static int getASMModifiers(Modifiers modifiers) {
        int mods = 0;
        if (modifiers == null) {
            return mods;
        }

        if (modifiers.hasModifier("public")) {
            mods += ACC_PUBLIC;
        }
        if (modifiers.hasModifier("protected")) {
            mods += ACC_PROTECTED;
        }
        if (modifiers.hasModifier("static")) {
            mods += ACC_STATIC;
        }
        if (modifiers.hasModifier("synchronized")) {
            mods += ACC_SYNCHRONIZED;
        }
        if (modifiers.hasModifier("abstract")) {
            mods += ACC_ABSTRACT;
        }

        return mods;
    }


    /**
     * Generate a field - static or instance.
     */
    private static void generateField(String fieldName, String type, int modifiers, ClassWriter cw) {
        cw.visitField(modifiers, fieldName, type, null/*value*/);
    }


    /**
     * Generate a delegate method - static or instance.
     * The generated code packs the method arguments into an object array
     * (wrapping primitive types in dragon.Primitive), invokes the static or
     * instance namespace invokeMethod() method, and then unwraps / returns
     * the result.
     */
    private static void generateMethod(String className, String fqClassName, String methodName, String returnType, String[] paramTypes, int modifiers, ClassWriter cw) {
        String[] exceptions = null;
        boolean isStatic = (modifiers & ACC_STATIC) != 0;

        if (returnType == null) // map loose return type to Object
        {
            returnType = OBJECT;
        }

        String methodDescriptor = getMethodDescriptor(returnType, paramTypes);

        // Generate method body
        CodeVisitor cv = cw.visitMethod(modifiers, methodName, methodDescriptor, exceptions);

        if ((modifiers & ACC_ABSTRACT) != 0) {
            return;
        }

        // Generate code to push the DRAGONTHIS or DRAGONSTATIC field
        if (isStatic) {
            cv.visitFieldInsn(GETSTATIC, fqClassName, DRAGONSTATIC + className, "Lcom/dragon/lang/ast/This;");
        } else {
            // Push 'this'
            cv.visitVarInsn(ALOAD, 0);

            // Get the instance field
            cv.visitFieldInsn(GETFIELD, fqClassName, DRAGONTHIS + className, "Lcom/dragon/lang/ast/This;");
        }

        // Push the name of the method as a constant
        cv.visitLdcInsn(methodName);

        // Generate code to push arguments as an object array
        generateParameterReifierCode(paramTypes, isStatic, cv);

        // Push nulls for various args of invokeMethod
        cv.visitInsn(ACONST_NULL); // dragonBasicInterpreter
        cv.visitInsn(ACONST_NULL); // callStack
        cv.visitInsn(ACONST_NULL); // callerinfo

        // Push the boolean constant 'true' (for declaredOnly)
        cv.visitInsn(ICONST_1);

        // Invoke the method This.invokeMethod( name, Class [] sig, boolean )
        cv.visitMethodInsn(INVOKEVIRTUAL, "com/dragon/lang/ast/This", "invokeMethod", Type.getMethodDescriptor(Type.getType(Object.class), new Type[]{Type.getType(String.class), Type.getType(Object[].class), Type.getType(DragonBasicInterpreter.class), Type.getType(CallStack.class), Type.getType(SimpleNode.class), Type.getType(Boolean.TYPE)}));

        // Generate code to unwrap dragon Primitive types
        cv.visitMethodInsn(INVOKESTATIC, "com/dragon/lang/ast/Primitive", "unwrap", "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Generate code to return the value
        generateReturnCode(returnType, cv);

        // Need to calculate this... just fudging here for now.
        cv.visitMaxs(20, 20);
    }


    /**
     * Generate a constructor.
     */
    void generateConstructor(int index, String[] paramTypes, int modifiers, ClassWriter cw) {
        /** offset after params of the args object [] var */
        final int argsVar = paramTypes.length + 1;
        /** offset after params of the ConstructorArgs var */
        final int consArgsVar = paramTypes.length + 2;

        String[] exceptions = null;
        String methodDescriptor = getMethodDescriptor("V", paramTypes);

        // Create this constructor method
        CodeVisitor cv = cw.visitMethod(modifiers, "<init>", methodDescriptor, exceptions);

        // Generate code to push arguments as an object array
        generateParameterReifierCode(paramTypes, false/*isStatic*/, cv);
        cv.visitVarInsn(ASTORE, argsVar);

        // Generate the code implementing the alternate constructor switch
        generateConstructorSwitch(index, argsVar, consArgsVar, cv);

        // Generate code to invoke the ClassGeneratorUtil initInstance() method

        // push 'this'
        cv.visitVarInsn(ALOAD, 0);

        // Push the class/constructor name as a constant
        cv.visitLdcInsn(className);

        // Push arguments as an object array
        cv.visitVarInsn(ALOAD, argsVar);

        // invoke the initInstance() method
        cv.visitMethodInsn(INVOKESTATIC, "com/dragon/lang/ast/ClassGeneratorUtil", "initInstance", "(L" + GeneratedClass.class.getName().replace('.', '/') + ";Ljava/lang/String;[Ljava/lang/Object;)V");

        cv.visitInsn(RETURN);

        // Need to calculate this... just fudging here for now.
        cv.visitMaxs(20, 20);
    }


    /**
     * Generate a switch with a branch for each possible alternate
     * constructor.  This includes all superclass constructors and all
     * constructors of this class.  The default branch of this switch is the
     * default superclass constructor.
     * <p/>
     * This method also generates the code to call the static
     * ClassGeneratorUtil
     * getConstructorArgs() method which inspects the scripted constructor to
     * find the alternate constructor signature (if any) and evalute the
     * arguments at runtime.  The getConstructorArgs() method returns the
     * actual arguments as well as the index of the constructor to call.
     */
    void generateConstructorSwitch(int consIndex, int argsVar, int consArgsVar, CodeVisitor cv) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();
        int cases = superConstructors.length + constructors.length;

        Label[] labels = new Label[cases];
        for (int i = 0; i < cases; i++) {
            labels[i] = new Label();
        }

        // Generate code to call ClassGeneratorUtil to get our switch index
        // and give us args...

        // push super class name
        cv.visitLdcInsn(superClass.getName()); // use superClassName var?

        // push class static This object
        cv.visitFieldInsn(GETSTATIC, fqClassName, DRAGONSTATIC + className, "Lcom/dragon/lang/ast/This;");

        // push args
        cv.visitVarInsn(ALOAD, argsVar);

        // push this constructor index number onto stack
        cv.visitIntInsn(BIPUSH, consIndex);

        // invoke the ClassGeneratorUtil getConstructorsArgs() method
        cv.visitMethodInsn(INVOKESTATIC, "com/dragon/lang/ast/ClassGeneratorUtil", "getConstructorArgs", "(Ljava/lang/String;Lcom/dragon/lang/ast/This;[Ljava/lang/Object;I)" + "Lcom/dragon/lang/ast/ClassGeneratorUtil$ConstructorArgs;");

        // store ConstructorArgs in consArgsVar
        cv.visitVarInsn(ASTORE, consArgsVar);

        // Get the ConstructorArgs selector field from ConstructorArgs

        // push ConstructorArgs
        cv.visitVarInsn(ALOAD, consArgsVar);
        cv.visitFieldInsn(GETFIELD, "com/dragon/lang/ast/ClassGeneratorUtil$ConstructorArgs", "selector", "I");

        // start switch
        cv.visitTableSwitchInsn(0/*min*/, cases - 1/*max*/, defaultLabel, labels);

        // generate switch body
        int index = 0;
        for (int i = 0; i < superConstructors.length; i++, index++) {
            doSwitchBranch(index, superClassName, getTypeDescriptors(superConstructors[i].getParameterTypes()), endLabel, labels, consArgsVar, cv);
        }
        for (int i = 0; i < constructors.length; i++, index++) {
            doSwitchBranch(index, fqClassName, constructors[i].getParamTypeDescriptors(), endLabel, labels, consArgsVar, cv);
        }

        // generate the default branch of switch
        cv.visitLabel(defaultLabel);
        // default branch always invokes no args super
        cv.visitVarInsn(ALOAD, 0); // push 'this'
        cv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V");

        // done with switch
        cv.visitLabel(endLabel);
    }

	/*
             Generate a branch of the constructor switch.  This method is called by
			 generateConstructorSwitch.
			 The code generated by this method assumes that the argument array is
			 on the stack.
		 */


    private static void doSwitchBranch(int index, String targetClassName, String[] paramTypes, Label endLabel, Label[] labels, int consArgsVar, CodeVisitor cv) {
        cv.visitLabel(labels[index]);
        //cv.visitLineNumber( index, labels[index] );
        cv.visitVarInsn(ALOAD, 0); // push this before args

        // Unload the arguments from the ConstructorArgs object
        for (String type : paramTypes) {
            final String method;
            if (type.equals("Z")) {
                method = "getBoolean";
            } else if (type.equals("B")) {
                method = "getByte";
            } else if (type.equals("C")) {
                method = "getChar";
            } else if (type.equals("S")) {
                method = "getShort";
            } else if (type.equals("I")) {
                method = "getInt";
            } else if (type.equals("J")) {
                method = "getLong";
            } else if (type.equals("D")) {
                method = "getDouble";
            } else if (type.equals("F")) {
                method = "getFloat";
            } else {
                method = "getObject";
            }

            // invoke the iterator method on the ConstructorArgs
            cv.visitVarInsn(ALOAD, consArgsVar); // push the ConstructorArgs
            String className = "com/dragon/lang/ast/ClassGeneratorUtil$ConstructorArgs";
            String retType;
            if (method.equals("getObject")) {
                retType = OBJECT;
            } else {
                retType = type;
            }
            cv.visitMethodInsn(INVOKEVIRTUAL, className, method, "()" + retType);
            // if it's an object type we must do a check cast
            if (method.equals("getObject")) {
                cv.visitTypeInsn(CHECKCAST, descriptorToClassName(type));
            }
        }

        // invoke the constructor for this branch
        String descriptor = getMethodDescriptor("V", paramTypes);
        cv.visitMethodInsn(INVOKESPECIAL, targetClassName, "<init>", descriptor);
        cv.visitJumpInsn(GOTO, endLabel);
    }


    private static String getMethodDescriptor(String returnType, String[] paramTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (String paramType : paramTypes) {
            sb.append(paramType);
        }
        sb.append(')').append(returnType);
        return sb.toString();
    }


    /**
     * Generate a superclass method delegate accessor method.
     * These methods are specially named methods which allow access to
     * overridden methods of the superclass (which the Java reflection API
     * normally does not allow).
     */
    // Maybe combine this with generateMethod()
    private static void generateSuperDelegateMethod(String superClassName, String methodName, String returnType, String[] paramTypes, int modifiers, ClassWriter cw) {
        String[] exceptions = null;

        if (returnType == null) // map loose return to Object
        {
            returnType = OBJECT;
        }

        String methodDescriptor = getMethodDescriptor(returnType, paramTypes);

        // Add method body
        CodeVisitor cv = cw.visitMethod(modifiers, "_dragonSuper" + methodName, methodDescriptor, exceptions);

        cv.visitVarInsn(ALOAD, 0);
        // Push vars
        int localVarIndex = 1;
        for (String paramType : paramTypes) {
            if (isPrimitive(paramType)) {
                cv.visitVarInsn(ILOAD, localVarIndex);
            } else {
                cv.visitVarInsn(ALOAD, localVarIndex);
            }
            localVarIndex += ((paramType.equals("D") || paramType.equals("J")) ? 2 : 1);
        }

        cv.visitMethodInsn(INVOKESPECIAL, superClassName, methodName, methodDescriptor);

        generatePlainReturnCode(returnType, cv);

        // Need to calculate this... just fudging here for now.
        cv.visitMaxs(20, 20);
    }


    boolean classContainsMethod(Class clas, String methodName, String[] paramTypes) {
        while (clas != null) {
            Method[] methods = clas.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    String[] methodParamTypes = getTypeDescriptors(method.getParameterTypes());
                    boolean found = true;
                    for (int j = 0; j < methodParamTypes.length; j++) {
                        if (!paramTypes[j].equals(methodParamTypes[j])) {
                            found = false;
                            break;
                        }
                    }
                    if (found) {
                        return true;
                    }
                }
            }

            clas = clas.getSuperclass();
        }

        return false;
    }


    /**
     * Generate return code for a normal bytecode
     */
    private static void generatePlainReturnCode(String returnType, CodeVisitor cv) {
        if (returnType.equals("V")) {
            cv.visitInsn(RETURN);
        } else if (isPrimitive(returnType)) {
            int opcode = IRETURN;
            if (returnType.equals("D")) {
                opcode = DRETURN;
            } else if (returnType.equals("F")) {
                opcode = FRETURN;
            } else if (returnType.equals("J"))  //long
            {
                opcode = LRETURN;
            }

            cv.visitInsn(opcode);
        } else {
            cv.visitTypeInsn(CHECKCAST, descriptorToClassName(returnType));
            cv.visitInsn(ARETURN);
        }
    }


    /**
     * Generates the code to reify the arguments of the given method.
     * For a method "int m (int i, String s)", this code is the bytecode
     * corresponding to the "new Object[] { new dragon.Primitive(i), s }"
     * expression.
     *
     * @param cv       the code visitor to be used to generate the bytecode.
     * @param isStatic the enclosing methods is static
     * @author Eric Bruneton
     * @author Pat Niemeyer
     */
    private static void generateParameterReifierCode(String[] paramTypes, boolean isStatic, final CodeVisitor cv) {
        cv.visitIntInsn(SIPUSH, paramTypes.length);
        cv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int localVarIndex = isStatic ? 0 : 1;
        for (int i = 0; i < paramTypes.length; ++i) {
            String param = paramTypes[i];
            cv.visitInsn(DUP);
            cv.visitIntInsn(SIPUSH, i);
            if (isPrimitive(param)) {
                int opcode;
                if (param.equals("F")) {
                    opcode = FLOAD;
                } else if (param.equals("D")) {
                    opcode = DLOAD;
                } else if (param.equals("J")) {
                    opcode = LLOAD;
                } else {
                    opcode = ILOAD;
                }

                String type = "com/dragon/lang/ast/Primitive";
                cv.visitTypeInsn(NEW, type);
                cv.visitInsn(DUP);
                cv.visitVarInsn(opcode, localVarIndex);
                String desc = param; // ok?
                cv.visitMethodInsn(INVOKESPECIAL, type, "<init>", "(" + desc + ")V");
            } else {
                // Technically incorrect here - we need to wrap null values
                // as dragon.Primitive.NULL.  However the This.invokeMethod()
                // will do that much for us.
                // We need to generate a conditional here to test for null
                // and return Primitive.NULL
                cv.visitVarInsn(ALOAD, localVarIndex);
            }
            cv.visitInsn(AASTORE);
            localVarIndex += ((param.equals("D") || param.equals("J")) ? 2 : 1);
        }
    }


    /**
     * Generates the code to unreify the result of the given method.  For a
     * method "int m (int i, String s)", this code is the bytecode
     * corresponding to the "((Integer)...).intValue()" expression.
     *
     * @param cv the code visitor to be used to generate the bytecode.
     * @author Eric Bruneton
     * @author Pat Niemeyer
     */
    private static void generateReturnCode(String returnType, CodeVisitor cv) {
        if (returnType.equals("V")) {
            cv.visitInsn(POP);
            cv.visitInsn(RETURN);
        } else if (isPrimitive(returnType)) {
            int opcode = IRETURN;
            String type;
            String meth;
            if (returnType.equals("B")) {
                type = "java/lang/Byte";
                meth = "byteValue";
            } else if (returnType.equals("I")) {
                type = "java/lang/Integer";
                meth = "intValue";
            } else if (returnType.equals("Z")) {
                type = "java/lang/Boolean";
                meth = "booleanValue";
            } else if (returnType.equals("D")) {
                opcode = DRETURN;
                type = "java/lang/Double";
                meth = "doubleValue";
            } else if (returnType.equals("F")) {
                opcode = FRETURN;
                type = "java/lang/Float";
                meth = "floatValue";
            } else if (returnType.equals("J")) {
                opcode = LRETURN;
                type = "java/lang/Long";
                meth = "longValue";
            } else if (returnType.equals("C")) {
                type = "java/lang/Character";
                meth = "charValue";
            } else /*if (returnType.equals("S") )*/ {
                type = "java/lang/Short";
                meth = "shortValue";
            }

            String desc = returnType;
            cv.visitTypeInsn(CHECKCAST, type); // type is correct here
            cv.visitMethodInsn(INVOKEVIRTUAL, type, meth, "()" + desc);
            cv.visitInsn(opcode);
        } else {
            cv.visitTypeInsn(CHECKCAST, descriptorToClassName(returnType));
            cv.visitInsn(ARETURN);
        }
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


    private static final ThreadLocal<NameSpace> CONTEXT_NAMESPACE = new ThreadLocal<NameSpace>();
    private static final ThreadLocal<DragonBasicInterpreter> CONTEXT_INTERPRETER = new ThreadLocal<DragonBasicInterpreter>();


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
        // (the case if using a this() alternate constuctor)
        // todo PeJoBo70 write test for this
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


    /**
     * Does the type descriptor string describe a primitive type?
     */
    private static boolean isPrimitive(String typeDescriptor) {
        return typeDescriptor.length() == 1; // right?
    }


    private static String[] getTypeDescriptors(Class[] cparams) {
        String[] sa = new String[cparams.length];
        for (int i = 0; i < sa.length; i++) {
            sa[i] = DragonType.getTypeDescriptor(cparams[i]);
        }
        return sa;
    }


    /**
     * If a non-array object type, remove the prefix "L" and suffix ";".
     */
    // Can this be factored out...?
    // Should be be adding the L...; here instead?
    private static String descriptorToClassName(String s) {
        if (s.startsWith("[") || !s.startsWith("L")) {
            return s;
        }
        return s.substring(1, s.length() - 1);
    }


    private static String getBaseName(String className) {
        int i = className.indexOf("$");
        if (i == -1) {
            return className;
        }

        return className.substring(i + 1);
    }


    /**
     * A ConstructorArgs object holds evaluated arguments for a constructor
     * call as well as the index of a possible alternate selector to invoke.
     * This object is used by the constructor switch.
     *
     * @see dragon.ClassGeneratorUtil#generateConstructor(int, String[], int, dragon.org.objectweb.asm.ClassWriter)
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
