package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.classpath.DragonClassManager;

import java.lang.reflect.Array;

class DragonType extends SimpleNode
        implements DragonClassManager.Listener {
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

    String descriptor;

    DragonType(int id) {
        super(id);
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
     * attempted through the namespace in order to resolve imports.
     * If it is not found and the name is non-compound we assume the default
     * package for the name.
     */
    public String getTypeDescriptor(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter, String defaultPackage) {
        // return cached type if available
        if (descriptor != null)
            return descriptor;

        String descriptor;
        //  first node will either be PrimitiveType or AmbiguousName
        SimpleNode node = getTypeNode();
        if (node instanceof DragonPrimitiveType)
            descriptor = getTypeDescriptor(((DragonPrimitiveType) node).type);
        else {
            String clasName = ((DragonAmbiguousName) node).text;
            DragonClassManager dcm = dragonBasicInterpreter.getClassManager();
            // Note: incorrect here - we are using the hack in dragon class
            // manager that allows lookup by base name.  We need to eliminate
            // this limitation by working through imports.  See notes in class
            // manager.
            String definingClass = dcm.getClassBeingDefined(clasName);

            Class clas = null;
            if (definingClass == null) {
                try {
                    clas = ((DragonAmbiguousName) node).toClass(
                            callstack, dragonBasicInterpreter);
                } catch (EvalError e) {
                    //throw new InterpreterException("unable to resolve type: "+e);
                    // ignore and try default package
                    //System.out.println("DragonType: "+node+" class not found");
                }
            } else
                clasName = definingClass;

            if (clas != null) {
                //System.out.println("found clazz: "+clazz);
                descriptor = getTypeDescriptor(clas);
            } else {
                if (defaultPackage == null || Name.isCompound(clasName))
                    descriptor = "L" + clasName.replace('.', '/') + ";";
                else
                    descriptor =
                            "L" + defaultPackage.replace('.', '/') + "/" + clasName + ";";
            }
        }

        for (int i = 0; i < arrayDims; i++)
            descriptor = "[" + descriptor;

        this.descriptor = descriptor;
        //System.out.println("DragonType: returning descriptor: "+descriptor);
        return descriptor;
    }

    public Class getType(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        // return cached type if available
        if (type != null)
            return type;

        //  first node will either be PrimitiveType or AmbiguousName
        SimpleNode node = getTypeNode();
        if (node instanceof DragonPrimitiveType)
            baseType = ((DragonPrimitiveType) node).getType();
        else
            baseType = ((DragonAmbiguousName) node).toClass(
                    callstack, dragonBasicInterpreter);

        if (arrayDims > 0) {
            try {
                // Get the type by constructing a prototype array with
                // arbitrary (zero) length in each dimension.
                int[] dims = new int[arrayDims]; // int array default zeros
                Object obj = Array.newInstance(baseType, dims);
                type = obj.getClass();
            } catch (Exception e) {
                throw new EvalError("Couldn't construct array type",
                        this, callstack);
            }
        } else
            type = baseType;

        // hack... sticking to first dragonBasicInterpreter that resolves this
        // see comments on type instance variable
        dragonBasicInterpreter.getClassManager().addListener(this);

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

    public static String getTypeDescriptor(Class clas) {
        if (clas == Boolean.TYPE) return "Z";
        if (clas == Character.TYPE) return "C";
        if (clas == Byte.TYPE) return "B";
        if (clas == Short.TYPE) return "S";
        if (clas == Integer.TYPE) return "I";
        if (clas == Long.TYPE) return "J";
        if (clas == Float.TYPE) return "F";
        if (clas == Double.TYPE) return "D";
        if (clas == Void.TYPE) return "V";
        // Is getName() ok?  test with 1.1
        String name = clas.getName().replace('.', '/');

        if (name.startsWith("[") || name.endsWith(";"))
            return name;
        else
            return "L" + name.replace('.', '/') + ";";
    }
}
