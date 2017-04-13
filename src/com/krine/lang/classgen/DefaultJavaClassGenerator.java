package com.krine.lang.classgen;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.asm.*;
import com.krine.lang.ast.*;
import com.krine.lang.classpath.GeneratedClass;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.Capabilities;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.krine.lang.ast.ClassGenerator.*;

/**
 * @author kiva
 * @date 2017/3/18
 */
class DefaultJavaClassGenerator implements IClassGenerator, Constants {
    private String className;
    /**
     * fully qualified class name (with package) e.g. foo/bar/Blah
     */
    private String fqClassName;
    private Class superClass;
    private String superClassName;
    private Class[] interfaces;
    private Variable[] vars;
    private Constructor[] superConstructors;
    private KrineMethodDelayEvaluated[] constructors;
    private KrineMethodDelayEvaluated[] methods;
    private NameSpace classStaticNameSpace;
    private Modifiers classModifiers;
    private boolean isInterface;

    /**
     * Translate krine.Modifiers into ASM modifier bitflags.
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
     * (wrapping primitive types in krine.Primitive), invokes the static or
     * instance nameSpace invokeMethod() method, and then unwraps / returns
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

        // Generate code to push the KRINE_THIS or KRINE_STATIC field
        if (isStatic) {
            cv.visitFieldInsn(GETSTATIC, fqClassName, KRINE_STATIC + className, "Lcom/krine/lang/ast/This;");
        } else {
            // Push 'this'
            cv.visitVarInsn(ALOAD, 0);

            // Get the instance field
            cv.visitFieldInsn(GETFIELD, fqClassName, KRINE_THIS + className, "Lcom/krine/lang/ast/This;");
        }

        // Push the name of the method as a constant
        cv.visitLdcInsn(methodName);

        // Generate code to push arguments as an object array
        generateParameterReifiedCode(paramTypes, isStatic, cv);

        // Push nulls for various args of invokeMethod
        cv.visitInsn(ACONST_NULL); // krineBasicInterpreter
        cv.visitInsn(ACONST_NULL); // callStack
        cv.visitInsn(ACONST_NULL); // callerInfo

        // Push the boolean constant 'true' (for declaredOnly)
        cv.visitInsn(ICONST_1);

        // Invoke the method This.invokeMethod( name, Class [] sig, boolean )
        cv.visitMethodInsn(INVOKEVIRTUAL, "com/krine/lang/ast/This", "invokeMethod", Type.getMethodDescriptor(Type.getType(Object.class), new Type[]{Type.getType(String.class), Type.getType(Object[].class), Type.getType(KrineBasicInterpreter.class), Type.getType(CallStack.class), Type.getType(SimpleNode.class), Type.getType(Boolean.TYPE)}));

        // Generate code to unwrap krine Primitive types
        cv.visitMethodInsn(INVOKESTATIC, "com/krine/lang/ast/Primitive", "unwrap", "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Generate code to return the value
        generateReturnCode(returnType, cv);

        // Need to calculate this... just fudging here for now.
        cv.visitMaxs(20, 20);
    }

    private static void doSwitchBranch(int index, String targetClassName, String[] paramTypes, Label endLabel, Label[] labels, int consArgsVar, CodeVisitor cv) {
        cv.visitLabel(labels[index]);
        //cv.visitLineNumber( index, labels[index] );
        cv.visitVarInsn(ALOAD, 0); // push this before args

        // Unload the arguments from the ConstructorArgs object
        for (String type : paramTypes) {
            final String method;
            switch (type) {
                case "Z":
                    method = "getBoolean";
                    break;
                case "B":
                    method = "getByte";
                    break;
                case "C":
                    method = "getChar";
                    break;
                case "S":
                    method = "getShort";
                    break;
                case "I":
                    method = "getInt";
                    break;
                case "J":
                    method = "getLong";
                    break;
                case "D":
                    method = "getDouble";
                    break;
                case "F":
                    method = "getFloat";
                    break;
                default:
                    method = "getObject";
                    break;
            }

            // invoke the iterator method on the ConstructorArgs
            cv.visitVarInsn(ALOAD, consArgsVar); // push the ConstructorArgs
            String className = "com/krine/lang/ast/ClassGenerator$ConstructorArgs";
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
        CodeVisitor cv = cw.visitMethod(modifiers, KRINE_SUPER + methodName, methodDescriptor, exceptions);

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

    /**
     * Generate return code for a normal bytecode
     */
    private static void generatePlainReturnCode(String returnType, CodeVisitor cv) {
        if (returnType.equals("V")) {
            cv.visitInsn(RETURN);
        } else if (isPrimitive(returnType)) {
            int opcode = IRETURN;
            switch (returnType) {
                case "D":
                    opcode = DRETURN;
                    break;
                case "F":
                    opcode = FRETURN;
                    break;
                case "J":
                    opcode = LRETURN;
                    break;
            }

            cv.visitInsn(opcode);
        } else {
            cv.visitTypeInsn(CHECKCAST, descriptorToClassName(returnType));
            cv.visitInsn(ARETURN);
        }
    }

	/*
             Generate a branch of the constructor switch.  This method is called by
			 generateConstructorSwitch.
			 The code generated by this method assumes that the argument array is
			 on the stack.
		 */

    /**
     * Generates the code to reify the arguments of the given method.
     * For a method "int m (int i, String s)", this code is the bytecode
     * corresponding to the "new Object[] { new krine.Primitive(i), s }"
     * expression.
     *
     * @param cv       the code visitor to be used to generate the bytecode.
     * @param isStatic the enclosing methods is static
     * @author Eric Bruneton
     * @author Pat Niemeyer
     */
    private static void generateParameterReifiedCode(String[] paramTypes, boolean isStatic, final CodeVisitor cv) {
        cv.visitIntInsn(SIPUSH, paramTypes.length);
        cv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int localVarIndex = isStatic ? 0 : 1;
        for (int i = 0; i < paramTypes.length; ++i) {
            String param = paramTypes[i];
            cv.visitInsn(DUP);
            cv.visitIntInsn(SIPUSH, i);
            if (isPrimitive(param)) {
                int opcode;
                switch (param) {
                    case "F":
                        opcode = FLOAD;
                        break;
                    case "D":
                        opcode = DLOAD;
                        break;
                    case "J":
                        opcode = LLOAD;
                        break;
                    default:
                        opcode = ILOAD;
                        break;
                }

                String type = "com/krine/lang/ast/Primitive";
                cv.visitTypeInsn(NEW, type);
                cv.visitInsn(DUP);
                cv.visitVarInsn(opcode, localVarIndex);
                cv.visitMethodInsn(INVOKESPECIAL, type, "<init>", "(" + param + ")V");
            } else {
                // Technically incorrect here - we need to wrap null values
                // as krine.Primitive.NULL.  However the This.invokeMethod()
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
     * Generates the code to un-reify the result of the given method.  For a
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
            String type = "";
            String method = "";
            switch (returnType) {
                case "B":
                    type = "java/lang/Byte";
                    method = "byteValue";
                    break;
                case "I":
                    type = "java/lang/Integer";
                    method = "intValue";
                    break;
                case "Z":
                    type = "java/lang/Boolean";
                    method = "booleanValue";
                    break;
                case "D":
                    opcode = DRETURN;
                    type = "java/lang/Double";
                    method = "doubleValue";
                    break;
                case "F":
                    opcode = FRETURN;
                    type = "java/lang/Float";
                    method = "floatValue";
                    break;
                case "J":
                    opcode = LRETURN;
                    type = "java/lang/Long";
                    method = "longValue";
                    break;
                case "C":
                    type = "java/lang/Character";
                    method = "charValue";
                    break;
                case "S":
                    type = "java/lang/Short";
                    method = "shortValue";
                    break;
            }

            cv.visitTypeInsn(CHECKCAST, type); // type is correct here
            cv.visitMethodInsn(INVOKEVIRTUAL, type, method, "()" + returnType);
            cv.visitInsn(opcode);
        } else {
            cv.visitTypeInsn(CHECKCAST, descriptorToClassName(returnType));
            cv.visitInsn(ARETURN);
        }
    }

    /**
     * @see com.krine.lang.ast.ClassGenerator#getConstructorArgs(String, This, Object[], int)
     */
    public static ClassGenerator.ConstructorArgs getConstructorArgs(String superClassName, This classStaticThis, Object[] consArgs, int index) {
        return ClassGenerator.getConstructorArgs(superClassName, classStaticThis, consArgs, index);
    }

    /**
     * Does the type descriptor string describe a primitive type?
     */
    private static boolean isPrimitive(String typeDescriptor) {
        return typeDescriptor.length() == 1; // right?
    }

    private static String[] getTypeDescriptors(Class[] paramClasses) {
        return ClassGenerator.getTypeDescriptors(paramClasses);
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

    @SuppressWarnings("SuspiciousToArrayCall")
    private void prepare(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, KrineMethodDelayEvaluated[] dragonMethods, NameSpace classStaticNameSpace, boolean isInterface) {
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

        // Split the methods into constructorList and regular methodList
        List<KrineMethod> constructorList = new ArrayList<>();
        List<KrineMethod> methodList = new ArrayList<>();
        String classBaseName = ClassGenerator.getBaseName(className); // for inner classes
        for (KrineMethodDelayEvaluated dragonMethod : dragonMethods) {
            if (dragonMethod.getName().equals(classBaseName)) {
                constructorList.add(dragonMethod);
                if ((packageName == null) && !Capabilities.haveAccessibility()) {
                    dragonMethod.makePublic();
                }
            } else {
                methodList.add(dragonMethod);
            }
        }

        this.constructors = constructorList.toArray(new KrineMethodDelayEvaluated[constructorList.size()]);
        this.methods = methodList.toArray(new KrineMethodDelayEvaluated[methodList.size()]);

        try {
            ClassGenerator.setLocalVariable(classStaticNameSpace, KRINE_CONSTRUCTORS, this.constructors, false);
        } catch (UtilEvalException e) {
            throw new InterpreterException("can't set constructorList var");
        }

        this.isInterface = isInterface;
    }

    @Override
    public byte[] generateClass(Modifiers classModifiers, String className, String packageName, Class superClass, Class[] interfaces, Variable[] vars, KrineMethodDelayEvaluated[] dragonMethods, NameSpace classStaticNameSpace, boolean isInterface) {
        prepare(classModifiers, className, packageName, superClass, interfaces, vars, dragonMethods, classStaticNameSpace, isInterface);
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

        String sourceFile = "Krine Generated via ASM (www.objectweb.org)";
        ClassWriter cw = new ClassWriter(false);
        cw.visit(classMods, fqClassName, superClassName, interfaceNames, sourceFile);

        if (!isInterface) {
            // Generate the krine instance 'This' reference holder field
            generateField(KRINE_THIS + className, "Lcom/krine/lang/ast/This;", ACC_PUBLIC, cw);

            // Generate the static krine static reference holder field
            generateField(KRINE_STATIC + className, "Lcom/krine/lang/ast/This;", ACC_PUBLIC + ACC_STATIC, cw);
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
            generateConstructor(DEFAULT_CONSTRUCTOR/*index*/, new String[0], ACC_PUBLIC, cw);
        }

        // Generate the delegate methods
        for (KrineMethodDelayEvaluated method : methods) {
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
        generateParameterReifiedCode(paramTypes, false/*isStatic*/, cv);
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
        cv.visitMethodInsn(INVOKESTATIC, "com/krine/lang/ast/ClassGenerator", "initInstance", "(L" + GeneratedClass.class.getName().replace('.', '/') + ";Ljava/lang/String;[Ljava/lang/Object;)V");

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
     * find the alternate constructor signature (if any) and evaluate the
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
        cv.visitFieldInsn(GETSTATIC, fqClassName, KRINE_STATIC + className, "Lcom/krine/lang/ast/This;");

        // push args
        cv.visitVarInsn(ALOAD, argsVar);

        // push this constructor index number onto stack
        cv.visitIntInsn(BIPUSH, consIndex);

        // invoke the ClassGeneratorUtil getConstructorsArgs() method
        cv.visitMethodInsn(INVOKESTATIC, "com/krine/lang/ast/ClassGenerator", "getConstructorArgs", "(Ljava/lang/String;Lcom/krine/lang/ast/This;[Ljava/lang/Object;I)" + "Lcom/krine/lang/ast/ClassGenerator$ConstructorArgs;");

        // store ConstructorArgs in consArgsVar
        cv.visitVarInsn(ASTORE, consArgsVar);

        // Get the ConstructorArgs selector field from ConstructorArgs

        // push ConstructorArgs
        cv.visitVarInsn(ALOAD, consArgsVar);
        cv.visitFieldInsn(GETFIELD, "com/krine/lang/ast/ClassGenerator$ConstructorArgs", "selector", "I");

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

    boolean classContainsMethod(Class clazz, String methodName, String[] paramTypes) {
        while (clazz != null) {
            Method[] methods = clazz.getDeclaredMethods();
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

            clazz = clazz.getSuperclass();
        }

        return false;
    }
}
