package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.classpath.KrineClassManager;
import com.krine.lang.utils.CallStack;

import java.lang.reflect.Array;

class KrineType extends SimpleNode
        implements KrineClassManager.Listener {
    String descriptor;
    /**
     * baseType is used during evaluation of full type and retained for the
     * case where we are an array type.
     * In the case where we are not an array this will be the same as type.
     */
    private Class baseType;
    /**
     * If we are an array type this will be non zero and indicate the
     * dimensionality of the array.  e.g. 2 for String[][];
     */
    private int arrayDims;
    /**
     * Internal cache of the type.  Cleared on classloader change.
     */
    private Class type;

    KrineType(int id) {
        super(id);
    }

    public static String getTypeDescriptor(Class clazz) {
        if (clazz == Boolean.TYPE) return "Z";
        if (clazz == Character.TYPE) return "C";
        if (clazz == Byte.TYPE) return "B";
        if (clazz == Short.TYPE) return "S";
        if (clazz == Integer.TYPE) return "I";
        if (clazz == Long.TYPE) return "J";
        if (clazz == Float.TYPE) return "F";
        if (clazz == Double.TYPE) return "D";
        if (clazz == Void.TYPE) return "V";
        // Is getName() ok?  test with 1.1
        String name = clazz.getName().replace('.', '/');

        if (name.startsWith("[") || name.endsWith(";"))
            return name;
        else
            return "L" + name.replace('.', '/') + ";";
    }

    /**
     * Used by the grammar to indicate dimensions of array types
     * during parsing.
     */
    public void addArrayDimension() {
        arrayDims++;
    }

    SimpleNode getTypeNode() {
        return (SimpleNode) jjtGetChild(0);
    }

    /**
     * Returns a class descriptor for this type.
     * If the type is an ambiguous name (object type) evaluation is
     * attempted through the nameSpace in order to resolve imports.
     * If it is not found and the name is non-compound we assume the default
     * package for the name.
     */
    public String getTypeDescriptor(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, String defaultPackage) {
        // return cached type if available
        if (descriptor != null)
            return descriptor;

        String descriptor;
        //  first node will either be PrimitiveType or AmbiguousName
        SimpleNode node = getTypeNode();
        if (node instanceof KrinePrimitiveType)
            descriptor = getTypeDescriptor(((KrinePrimitiveType) node).type);
        else {
            String className = ((KrineAmbiguousName) node).text;
            KrineClassManager dcm = krineBasicInterpreter.getClassManager();
            // Note: incorrect here - we are using the hack in krine class
            // manager that allows lookup by base name.  We need to eliminate
            // this limitation by working through imports.  See notes in class
            // manager.
            String definingClass = dcm.getClassBeingDefined(className);

            Class clazz = null;
            if (definingClass == null) {
                try {
                    clazz = ((KrineAmbiguousName) node).toClass(
                            callStack, krineBasicInterpreter);
                } catch (EvalError e) {
                    //throw new InterpreterException("unable to resolve type: "+e);
                    // ignore and try default package
                    //System.out.println("KrineType: "+node+" class not found");
                }
            } else
                className = definingClass;

            if (clazz != null) {
                //System.out.println("found clazz: "+clazz);
                descriptor = getTypeDescriptor(clazz);
            } else {
                if (defaultPackage == null || Name.isCompound(className))
                    descriptor = "L" + className.replace('.', '/') + ";";
                else
                    descriptor =
                            "L" + defaultPackage.replace('.', '/') + "/" + className + ";";
            }
        }

        for (int i = 0; i < arrayDims; i++)
            descriptor = "[" + descriptor;

        this.descriptor = descriptor;
        //System.out.println("KrineType: returning descriptor: "+descriptor);
        return descriptor;
    }

    public Class getType(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        // return cached type if available
        if (type != null)
            return type;

        //  first node will either be PrimitiveType or AmbiguousName
        SimpleNode node = getTypeNode();
        if (node instanceof KrinePrimitiveType)
            baseType = ((KrinePrimitiveType) node).getType();
        else
            baseType = ((KrineAmbiguousName) node).toClass(
                    callStack, krineBasicInterpreter);

        if (arrayDims > 0) {
            try {
                // Get the type by constructing a prototype array with
                // arbitrary (zero) length in each dimension.
                int[] dims = new int[arrayDims]; // int array default zeros
                Object obj = Array.newInstance(baseType, dims);
                type = obj.getClass();
            } catch (Exception e) {
                throw new EvalError("Couldn't construct array type",
                        this, callStack);
            }
        } else
            type = baseType;

        // hack... sticking to first krineBasicInterpreter that resolves this
        // see comments on type instance variable
        krineBasicInterpreter.getClassManager().addListener(this);

        return type;
    }

    /**
     * baseType is used during evaluation of full type and retained for the
     * case where we are an array type.
     * In the case where we are not an array this will be the same as type.
     */
    public Class getBaseType() {
        return baseType;
    }

    /**
     * If we are an array type this will be non zero and indicate the
     * dimensionality of the array.  e.g. 2 for String[][];
     */
    public int getArrayDims() {
        return arrayDims;
    }

    public void classLoaderChanged() {
        type = null;
        baseType = null;
    }
}
