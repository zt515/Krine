package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.UtilTargetException;
import com.krine.lang.classpath.ClassIdentifier;
import com.krine.lang.classpath.ClassPathException;
import com.krine.lang.classpath.KrineClassManager;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.reflect.ReflectException;
import com.krine.lang.utils.CallStack;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

/**
 * Name() is a somewhat ambiguous thing in the grammar and so is this.
 * <p>
 * <p>
 * This class is a name resolver.  It holds a possibly ambiguous dot
 * separated name and reference to a nameSpace in which it allegedly lives.
 * It provides methods that attempt to resolve the name to various types of
 * entities: e.g. an Object, a Class, a declared scripted Krine method.
 * <p>
 * <p>
 * Name objects are created by the factory method NameSpace getNameResolver(),
 * which caches them subject to a class nameSpace change.  This means that
 * we can cache information about various types of resolution here.
 * Currently very little if any information is cached.  However with a future
 * "optimize" setting that defeats certain dynamic behavior we might be able
 * to cache quite a bit.
 */
/*
    <strong>Implementation notes</strong>
	<pre>
	Thread safety: all of the work methods in this class must be synchronized
	because they share the internal intermediate evaluation state.

	Note about invokeMethod():  We could simply use resolveMethod and return
	the MethodInvoker (KrineMethod or JavaMethod) however there is no easy way
	for the AST to use this as it doesn't have type
	information about the target to resolve overloaded methods.
	(In Java, overloaded methods are resolved at compile time... here they
	are, of necessity, dynamic).  So it would have to do what we do here
	and cache by signature.  We now do that for the client in Reflect.java.

	Note on this.caller resolution:
	Although references like these do work:

		this.caller.caller.caller...   // works

	the equivalent using successive calls:

		// does *not* work
		for( caller=this.caller; caller != null; caller = caller.caller );

	is prohibited by the restriction that you can only call .caller on a 
	literal	this or caller reference.  The effect is that magic caller 
	reference only works through the current 'this' reference.
	The real explanation is that This references do not really know anything
	about their depth on the call stack.  It might even be hard to define
	such a thing...

	For those purposes we provide :

		this.callStack

	</pre>
*/
public class Name implements Serializable {
    private static String FINISHED = null; // null evalName and we're finished
    // These do not change during evaluation
    public NameSpace nameSpace;

    // ---------------------------------------------------------
    // The following instance variables mutate during evaluation and should
    // be reset by the reset() method where necessary

    // For evaluation
    String value = null;
    /**
     * The result is a class
     */
    Class asClass;
    /**
     * The result is a static method call on the following class
     */
    Class classOfStaticMethod;
    /**
     * Remaining text to evaluate
     */
    private String evalName;
    /**
     * The last part of the name evaluated.  This is really only used for
     * this, caller, and super resolution.
     */
    private String lastEvalName;

    //
    //  End mutable instance variables.
    // ---------------------------------------------------------

    // Begin Cached result structures
    // These are optimizations

    // Note: it's ok to cache class resolution here because when the class
    // space changes the nameSpace will discard cached names.
    private Object evalBaseObject;    // base object for current eval
    private int callStackDepth;        // number of times eval hit 'this.caller'

    // End Cached result structures

    /**
     * This constructor should *not* be used in general.
     * Use NameSpace getNameResolver() which supports caching.
     *
     * @see NameSpace getNameResolver().
     */
    // I wish I could make this "friendly" to only NameSpace
    Name(NameSpace nameSpace, String s) {
        this.nameSpace = nameSpace;
        value = s;
    }

    /**
     * @return the enclosing class body nameSpace or null if not in a class.
     */
    static NameSpace getClassNameSpace(NameSpace thisNameSpace) {
        // is a class instance
        //if ( thisNameSpace.classInstance != null )
        if (thisNameSpace.isClass)
            return thisNameSpace;

        if (thisNameSpace.isMethod
                && thisNameSpace.getParent() != null
                //&& thisNameSpace.getParent().classInstance != null
                && thisNameSpace.getParent().isClass
                )
            return thisNameSpace.getParent();

        return null;
    }

    public static boolean isCompound(String value) {
        return value.indexOf('.') != -1;
        //return countParts(value) > 1;
    }

    static int countParts(String value) {
        if (value == null)
            return 0;

        int count = 0;
        int index = -1;
        while ((index = value.indexOf('.', index + 1)) != -1)
            count++;
        return count + 1;
    }

    static String prefix(String value) {
        if (!isCompound(value))
            return null;

        return prefix(value, countParts(value) - 1);
    }

    static String prefix(String value, int parts) {
        if (parts < 1)
            return null;

        int count = 0;
        int index = -1;

        while (((index = value.indexOf('.', index + 1)) != -1)
                && (++count < parts)) {
        }

        return (index == -1) ? value : value.substring(0, index);
    }

    static String suffix(String name) {
        if (!isCompound(name))
            return null;

        return suffix(name, countParts(name) - 1);
    }

    public static String suffix(String value, int parts) {
        if (parts < 1)
            return null;

        int count = 0;
        int index = value.length() + 1;

        while (((index = value.lastIndexOf('.', index - 1)) != -1)
                && (++count < parts)) ;

        return (index == -1) ? value : value.substring(index + 1);
    }

    private void reset() {
        evalName = value;
        evalBaseObject = null;
        callStackDepth = 0;
    }

    /**
     * Resolve possibly complex name to an object value.
     * <p>
     * Throws EvalError on various failures.
     * A null object value is indicated by a Primitive.NULL.
     * A return type of Primitive.VOID comes from attempting to access
     * an undefined variable.
     * <p>
     * Some cases:
     * myVariable
     * myVariable.foo
     * myVariable.foo.bar
     * java.awt.GridBagConstraints.BOTH
     * my.package.stuff.MyClass.someField.someField...
     * <p>
     * KrineInterpreter reference is necessary to allow resolution of
     * "this.krineBasicInterpreter" magic leftValue.
     * CallStack reference is necessary to allow resolution of
     * "this.caller" magic leftValue.
     * "this.callStack" magic leftValue.
     */
    public Object toObject(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws UtilEvalException {
        return toObject(callStack, krineBasicInterpreter, false);
    }

    /**
     * @param forceClass if true then resolution will only produce a class.
     *                   This is necessary to disambiguate in cases where the grammar knows
     *                   that we want a class; where in general the var path may be taken.
     * @see #toObject(CallStack, KrineBasicInterpreter)
     */
    synchronized public Object toObject(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter, boolean forceClass)
            throws UtilEvalException {
        reset();

        Object obj = null;
        while (evalName != null)
            obj = consumeNextObjectField(
                    callStack, krineBasicInterpreter, forceClass, false/*autoalloc*/);

        if (obj == null)
            throw new InterpreterException("null value in toObject()");

        return obj;
    }

    private Object completeRound(
            String lastEvalName, String nextEvalName, Object returnObject) {
        if (returnObject == null)
            throw new InterpreterException("lastEvalName = " + lastEvalName);
        this.lastEvalName = lastEvalName;
        this.evalName = nextEvalName;
        this.evalBaseObject = returnObject;
        return returnObject;
    }

    // Static methods that operate on compound ('.' separated) names
    // I guess we could move these to StringUtil someday

    /**
     * Get the next object by consuming one or more components of evalName.
     * Often this consumes just one component, but if the name is a classname
     * it will consume all of the components necessary to make the class
     * identifier.
     */
    private Object consumeNextObjectField(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter,
            boolean forceClass, boolean autoAllocateThis)
            throws UtilEvalException {
        /*
            Is it a simple variable name?
			Doing this first gives the correct Java precedence for vars
			vs. imported class names (at least in the simple case - see
			tests/precedence1.krine).  It should also speed things up a bit.
		*/
        if ((evalBaseObject == null && !isCompound(evalName))
                && !forceClass) {
            Object obj = resolveThisFieldReference(
                    callStack, nameSpace, krineBasicInterpreter, evalName, false);

            if (obj != Primitive.VOID)
                return completeRound(evalName, FINISHED, obj);
        }

		/*
            Is it a krine script variable reference?
			If we're just starting the eval of name (no base object)
			or we're evaluating relative to a This type reference check.
		*/
        String varName = prefix(evalName, 1);
        if ((evalBaseObject == null || evalBaseObject instanceof This)
                && !forceClass) {
            if (KrineBasicInterpreter.DEBUG)
                KrineBasicInterpreter.debug("trying to resolve variable: " + varName);

            Object obj;
            // switch nameSpace and special var visibility
            if (evalBaseObject == null) {
                obj = resolveThisFieldReference(
                        callStack, nameSpace, krineBasicInterpreter, varName, false);
            } else {
                obj = resolveThisFieldReference(
                        callStack, ((This) evalBaseObject).namespace,
                        krineBasicInterpreter, varName, true);
            }

            if (obj != Primitive.VOID) {
                // Resolved the variable
                if (KrineBasicInterpreter.DEBUG)
                    KrineBasicInterpreter.debug("resolved variable: " + varName +
                            " in nameSpace: " + nameSpace);

                return completeRound(varName, suffix(evalName), obj);
            }
        }

		/*
			Is it a class name?
			If we're just starting eval of name try to make it, else fail.
		*/
        if (evalBaseObject == null) {
            if (KrineBasicInterpreter.DEBUG)
                KrineBasicInterpreter.debug("trying class: " + evalName);

			/*
                Keep adding parts until we have a class
			*/
            Class clazz = null;
            int i = 1;
            String className = null;
            for (; i <= countParts(evalName); i++) {
                className = prefix(evalName, i);
                if ((clazz = nameSpace.getClass(className)) != null)
                    break;
            }

            if (clazz != null) {
                return completeRound(
                        className,
                        suffix(evalName, countParts(evalName) - i),
                        new ClassIdentifier(clazz)
                );
            }
            // not a class (or variable per above)
            if (KrineBasicInterpreter.DEBUG)
                KrineBasicInterpreter.debug("not a class, trying var prefix " + evalName);
        }

        // No variable or class found in 'this' type ref.
        // if autoAllocateThis then create one; a child 'this'.
        if ((evalBaseObject == null || evalBaseObject instanceof This)
                && !forceClass && autoAllocateThis) {
            NameSpace targetNameSpace =
                    (evalBaseObject == null) ?
                            nameSpace : ((This) evalBaseObject).namespace;
            Object obj = new NameSpace(
                    targetNameSpace, "auto: " + varName).getThis(krineBasicInterpreter);
            targetNameSpace.setVariable(varName, obj, false);
            return completeRound(varName, suffix(evalName), obj);
        }

		/*
            If we didn't find a class or variable name (or prefix) above
			there are two possibilities:

			- If we are a simple name then we can pass as a void variable
			reference.
			- If we are compound then we must fail at this point.
		*/
        if (evalBaseObject == null) {
            if (!isCompound(evalName)) {
                return completeRound(evalName, FINISHED, Primitive.VOID);
            } else
                throw new UtilEvalException(
                        "Class or variable not found: " + evalName);
        }

		/*
			--------------------------------------------------------
			After this point we're definitely evaluating relative to
			a base object.
			--------------------------------------------------------
		*/

		/*
			Do some basic validity checks.
		*/

        if (evalBaseObject == Primitive.NULL) // previous round produced null
            throw new UtilTargetException(new NullPointerException(
                    "Null Pointer while evaluating: " + value));

        if (evalBaseObject == Primitive.VOID) // previous round produced void
            throw new UtilEvalException(
                    "Undefined variable or class name while evaluating: " + value);

        if (evalBaseObject instanceof Primitive)
            throw new UtilEvalException("Can't treat primitive like an object. " +
                    "Error while evaluating: " + value);

		/*
            Resolve relative to a class type
			static leftValue, inner class, ?
		*/
        if (evalBaseObject instanceof ClassIdentifier) {
            Class clazz = ((ClassIdentifier) evalBaseObject).getTargetClass();
            String field = prefix(evalName, 1);

            // Class qualified 'this' reference from inner class.
            // e.g. 'MyOuterClass.this'
            if (field.equals("this")) {
                // find the enclosing class instance space of the class name
                NameSpace ns = nameSpace;
                while (ns != null) {
                    // getClassInstance() throws exception if not there
                    if (ns.classInstance != null
                            && ns.classInstance.getClass() == clazz
                            )
                        return completeRound(
                                field, suffix(evalName), ns.classInstance);
                    ns = ns.getParent();
                }
                throw new UtilEvalException(
                        "Can't find enclosing 'this' instance of class: " + clazz);
            }

            Object obj = null;
            // static leftValue?
            try {
                if (KrineBasicInterpreter.DEBUG)
                    KrineBasicInterpreter.debug("Name call to getStaticFieldValue, class: "
                            + clazz + ", leftValue:" + field);
                obj = Reflect.getStaticFieldValue(clazz, field);
            } catch (ReflectException e) {
                if (KrineBasicInterpreter.DEBUG)
                    KrineBasicInterpreter.debug("leftValue reflect error: " + e);
            }

            // inner class?
            if (obj == null) {
                String iclass = clazz.getName() + "$" + field;
                Class c = nameSpace.getClass(iclass);
                if (c != null)
                    obj = new ClassIdentifier(c);
            }

            if (obj == null)
                throw new UtilEvalException(
                        "No static leftValue or inner class: "
                                + field + " of " + clazz);

            return completeRound(field, suffix(evalName), obj);
        }

		/*
			If we've fallen through here we are no longer resolving to
			a class type.
		*/
        if (forceClass)
            throw new UtilEvalException(
                    value + " does not resolve to a class name.");

		/*
            Some kind of leftValue access?
		*/

        String field = prefix(evalName, 1);

        // length access on array?
        if (field.equals("length") && evalBaseObject.getClass().isArray()) {
            Object obj = new Primitive(Array.getLength(evalBaseObject));
            return completeRound(field, suffix(evalName), obj);
        }

        // Check for leftValue on object
        // Note: could eliminate throwing the exception somehow
        try {
            Object obj = Reflect.getObjectFieldValue(evalBaseObject, field);
            return completeRound(field, suffix(evalName), obj);
        } catch (ReflectException e) { /* not a leftValue */ }

        // if we get here we have failed
        throw new UtilEvalException(
                "Cannot access leftValue: " + field + ", on object: " + evalBaseObject);
    }

    /**
     * Resolve a variable relative to a This reference.
     * <p>
     * This is the general variable resolution method, accomodating special
     * fields from the This context.  Together the nameSpace and krineBasicInterpreter
     * comprise the This context.  The callStack, if available allows for the
     * this.caller construct.
     * Optionally interpret special "magic" leftValue names: e.g. krineBasicInterpreter.
     * <p/>
     *
     * @param callStack     may be null, but this is only legitimate in special
     *                      cases where we are sure resolution will not involve this.caller.
     * @param thisNameSpace the nameSpace of the this reference (should be the
     *                      same as the top of the stack?
     */
    Object resolveThisFieldReference(
            CallStack callStack, NameSpace thisNameSpace, KrineBasicInterpreter krineBasicInterpreter,
            String varName, boolean specialFieldsVisible)
            throws UtilEvalException {
        if (varName.equals("this")) {
			/*
				Somewhat of a hack.  If the special fields are visible (we're
				operating relative to a 'this' type already) disallow further
				.this references to prevent user from skipping to things like
				super.this.caller
			*/
            if (specialFieldsVisible)
                throw new UtilEvalException("Redundant to call .this on This type");

            // Allow getThis() to work through BlockNameSpace to the method
            // nameSpace
            // XXX re-eval this... do we need it?
            This ths = thisNameSpace.getThis(krineBasicInterpreter);
            thisNameSpace = ths.getNameSpace();
            Object result = ths;

            NameSpace classNameSpace = getClassNameSpace(thisNameSpace);
            if (classNameSpace != null) {
                if (isCompound(evalName))
                    result = classNameSpace.getThis(krineBasicInterpreter);
                else
                    result = classNameSpace.getClassInstance();
            }

            return result;
        }

		/*
			Some duplication for "super".  See notes for "this" above
			If we're in an enclsing class instance and have a superclass
			instance our super is the superclass instance.
		*/
        if (varName.equals("super")) {
            //if ( specialFieldsVisible )
            //throw new UtilEvalException("Redundant to call .this on This type");

            // Allow getSuper() to through BlockNameSpace to the method's super
            This ths = thisNameSpace.getSuper(krineBasicInterpreter);
            thisNameSpace = ths.getNameSpace();
            // super is now the closure's super or class instance

            // XXXX re-evaluate this
            // can getSuper work by itself now?
            // If we're a class instance and the parent is also a class instance
            // then super means our parent.
            if (
                    thisNameSpace.getParent() != null
                            && thisNameSpace.getParent().isClass
                    )
                ths = thisNameSpace.getParent().getThis(krineBasicInterpreter);

            return ths;
        }

        Object obj = null;

        if (varName.equals("global"))
            obj = thisNameSpace.getGlobal(krineBasicInterpreter);

        if (obj == null && specialFieldsVisible) {
            switch (varName) {
                case "nameSpace":
                    obj = thisNameSpace;
                    break;
                case "variables":
                    obj = thisNameSpace.getVariableNames();
                    break;
                case "methods":
                    obj = thisNameSpace.getMethodNames();
                    break;
                case "krineBasicInterpreter":
                    if (lastEvalName.equals("this"))
                        obj = krineBasicInterpreter;
                    else
                        throw new UtilEvalException(
                                "Can only call .krineBasicInterpreter on literal 'this'");
                    break;
            }
        }

        if (obj == null && specialFieldsVisible && varName.equals("caller")) {
            if (lastEvalName.equals("this") || lastEvalName.equals("caller")) {
                // get the previous context (see notes for this class)
                if (callStack == null)
                    throw new InterpreterException("no callStack");
                obj = callStack.get(++callStackDepth).getThis(
                        krineBasicInterpreter);
            } else
                throw new UtilEvalException(
                        "Can only call .caller on literal 'this' or literal '.caller'");

            // early return
            return obj;
        }

        if (obj == null && specialFieldsVisible
                && varName.equals("callStack")) {
            if (lastEvalName.equals("this")) {
                // get the previous context (see notes for this class)
                if (callStack == null)
                    throw new InterpreterException("no callStack");
                obj = callStack;
            } else
                throw new UtilEvalException(
                        "Can only call .callStack on literal 'this'");
        }


        if (obj == null)
            obj = thisNameSpace.getVariable(varName);

        if (obj == null)
            throw new InterpreterException("null this leftValue ref:" + varName);

        return obj;
    }

    /**
     * Check the cache, else use toObject() to try to resolve to a class
     * identifier.
     *
     * @throws ClassNotFoundException on class not found.
     * @throws ClassPathException     (type of EvalError) on special case of
     *                                ambiguous unqualified name after super import.
     */
    synchronized public Class toClass()
            throws ClassNotFoundException, UtilEvalException {
        if (asClass != null)
            return asClass;

        reset();

        // "var" means untyped, return null class
        if (evalName.equals("var"))
            return asClass = null;

		/* Try straightforward class name first */
        Class clazz = nameSpace.getClass(evalName);

        if (clazz == null) {
            /*
                Try toObject() which knows how to work through inner classes
				and see what we end up with
			*/
            Object obj = null;
            try {
                // Null krineBasicInterpreter and callStack references.
                // class only resolution should not require them.
                obj = toObject(null, null, true);
            } catch (UtilEvalException ignored) {
            }// couldn't resolve it

            if (obj instanceof ClassIdentifier)
                clazz = ((ClassIdentifier) obj).getTargetClass();
        }

        if (clazz == null)
            throw new ClassNotFoundException(
                    "Class: " + value + " not found in nameSpace");

        asClass = clazz;
        return asClass;
    }

    /*
    */
    synchronized public LeftValue toLeftValue(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws UtilEvalException {
        // Should clean this up to a single return statement
        reset();
        LeftValue lhs;

        // Simple (non-compound) variable assignment e.g. x=5;
        if (!isCompound(evalName)) {
            if (evalName.equals("this"))
                throw new UtilEvalException("Can't assign to 'this'.");

            // KrineInterpreter.debug("Simple var LeftValue...");
            lhs = new LeftValue(nameSpace, evalName, false/*bubble up if allowed*/);
            return lhs;
        }

        // Field e.g. foo.bar=5;
        Object obj = null;
        try {
            while (evalName != null && isCompound(evalName)) {
                obj = consumeNextObjectField(callStack, krineBasicInterpreter,
                        false/*forcclass*/, true/*autoallocthis*/);
            }
        } catch (UtilEvalException e) {
            throw new UtilEvalException("LeftValue evaluation: " + e.getMessage());
        }

        // Finished eval and its a class.
        if (evalName == null && obj instanceof ClassIdentifier)
            throw new UtilEvalException("Can't assign to class: " + value);

        if (obj == null)
            throw new UtilEvalException("Error in LeftValue: " + value);

        // e.g. this.x=5;  or someThisType.x=5;
        if (obj instanceof This) {
            // disallow assignment to magic fields
            if (
                    evalName.equals("nameSpace")
                            || evalName.equals("variables")
                            || evalName.equals("methods")
                            || evalName.equals("caller")
                    )
                throw new UtilEvalException(
                        "Can't assign to special variable: " + evalName);

            KrineBasicInterpreter.debug("found This reference evaluating LeftValue");
			/*
                If this was a literal "super" reference then we allow recursion
				in setting the variable to get the normal effect of finding the
				nearest definition starting at the super scope.  On any other
				resolution qualified by a 'this' type reference we want to set
				the variable directly in that scope. e.g. this.x=5;  or
				someThisType.x=5;

				In the old scoping rules super didn't do this.
			*/
            boolean localVar = !lastEvalName.equals("super");
            return new LeftValue(((This) obj).namespace, evalName, localVar);
        }

        if (evalName != null) {
            try {
                if (obj instanceof ClassIdentifier) {
                    Class clazz = ((ClassIdentifier) obj).getTargetClass();
                    lhs = Reflect.getLHSStaticField(clazz, evalName);
                    return lhs;
                } else {
                    lhs = Reflect.getLHSObjectField(obj, evalName);
                    return lhs;
                }
            } catch (ReflectException e) {
                throw new UtilEvalException("Field access: " + e);
            }
        }

        throw new InterpreterException("Internal error in lhs...");
    }

    /**
     * Invoke the method identified by this name.
     * Performs caching of method resolution using SignatureKey.
     * <p>
     * <p>
     * Name contains a wholly unqualified messy name; resolve it to
     * ( object | static prefix ) + method name and invoke.
     * <p>
     * <p>
     * The krineBasicInterpreter is necessary to support 'this.krineBasicInterpreter' references
     * in the called code. (e.g. debug());
     * <p>
     * <p>
     * <pre>
     * Some cases:
     *
     * // dynamic
     * local();
     * myVariable.foo();
     * myVariable.bar.blah.foo();
     * // static
     * java.lang.Integer.getInteger("foo");
     * </pre>
     */
    public Object invokeMethod(
            KrineBasicInterpreter krineBasicInterpreter, Object[] args, CallStack callStack,
            SimpleNode callerInfo
    )
            throws UtilEvalException, EvalError, ReflectException, InvocationTargetException {
        String methodName = Name.suffix(value, 1);
        KrineClassManager dcm = krineBasicInterpreter.getClassManager();
        NameSpace namespace = callStack.top();

        // Optimization - If classOfStaticMethod is set then we have already
        // been here and determined that this is a static method invocation.
        // Note: maybe factor this out with path below... clean up.
        if (classOfStaticMethod != null) {
            return Reflect.invokeStaticMethod(
                    dcm, classOfStaticMethod, methodName, args);
        }

        if (!Name.isCompound(value))
            return invokeLocalMethod(
                    krineBasicInterpreter, args, callStack, callerInfo);

        // Note: if we want methods declared inside blocks to be accessible via
        // this.methodName() inside the block we could handle it here as a
        // special case.  See also resolveThisFieldReference() special handling
        // for BlockNameSpace case.  They currently work via the direct name
        // e.g. methodName().

        String prefix = Name.prefix(value);

        // Superclass method invocation? (e.g. super.foo())
        if (prefix.equals("super") && Name.countParts(value) == 2) {
            // Allow getThis() to work through block namespaces first
            This ths = namespace.getThis(krineBasicInterpreter);
            NameSpace thisNameSpace = ths.getNameSpace();
            NameSpace classNameSpace = getClassNameSpace(thisNameSpace);
            if (classNameSpace != null) {
                Object instance = classNameSpace.getClassInstance();
                return ClassGenerator.getClassGenerator()
                        .invokeSuperclassMethod(dcm, instance, methodName, args);
            }
        }

        // Find target object or class identifier
        Name targetName = namespace.getNameResolver(prefix);
        Object obj = targetName.toObject(callStack, krineBasicInterpreter);

        if (obj == Primitive.VOID)
            throw new UtilEvalException("Attempt to resolve method: " + methodName
                    + "() on undefined variable or class name: " + targetName);

        // if we've got an object, resolve the method
        if (!(obj instanceof ClassIdentifier)) {

            if (obj instanceof Primitive) {

                if (obj == Primitive.NULL)
                    throw new UtilTargetException(new NullPointerException(
                            "Null Pointer in Method Invocation"));

                // some other primitive
                // should avoid calling methods on primitive, as we do
                // in Name (can't treat primitive like an object message)
                // but the hole is useful right now.
                if (KrineBasicInterpreter.DEBUG)
                    KrineBasicInterpreter.debug(
                            "Attempt to access method on primitive..."
                                    + " allowing com.krine.lang.ast.Primitive to peek through for debugging");
            }

            // found an object and it's not an undefined variable
            return Reflect.invokeObjectMethod(
                    obj, methodName, args, krineBasicInterpreter, callStack, callerInfo);
        }

        // It's a class

        // try static method
        if (KrineBasicInterpreter.DEBUG)
            KrineBasicInterpreter.debug("invokeMethod: trying static - " + targetName);

        Class clazz = ((ClassIdentifier) obj).getTargetClass();

        // cache the fact that this is a static method invocation on this class
        classOfStaticMethod = clazz;

        if (clazz != null)
            return Reflect.invokeStaticMethod(dcm, clazz, methodName, args);

        // return null; ???
        throw new UtilEvalException("invokeMethod: unknown target: " + targetName);
    }

    /**
     * Invoke a locally declared method or a krine command.
     * If the method is not already declared in the nameSpace then try
     * to load it as a resource from the imported command path (e.g.
     * /krine/commands)
     */
	/*
        Note: the krine command code should probably not be here...  we need to
		scope it by the nameSpace that imported the command... so it probably
		needs to be integrated into NameSpace.
	*/
    private Object invokeLocalMethod(
            KrineBasicInterpreter krineBasicInterpreter, Object[] args, CallStack callStack,
            SimpleNode callerInfo
    )
            throws EvalError/*, ReflectException, InvocationTargetException*/ {
        if (KrineBasicInterpreter.DEBUG)
            KrineBasicInterpreter.debug("invokeLocalMethod: " + value);
        if (krineBasicInterpreter == null)
            throw new InterpreterException(
                    "invokeLocalMethod: krineBasicInterpreter = null");

        String methodName = value;
        Class[] argTypes = Types.getTypes(args);

        // Check for existing method
        KrineMethod method;
        try {
            method = nameSpace.getMethod(methodName, argTypes);
        } catch (UtilEvalException e) {
            throw e.toEvalError(
                    "Local method invocation", callerInfo, callStack);
        }

        // If defined, invoke it
        if (method != null) {
            // We only check private when we call local method.
            // Why? Because Module methods are all local methods.
            // We only prevent modules to call main program's methods.
            // But there's no limit when calling a Java method.
            NameSpace checkNameSpace = nameSpace.isMethod ? nameSpace.getParent() : nameSpace;
            if (method.getModifiers().hasModifier("private")
                    && method.getDeclaringNameSpace() != checkNameSpace) {
                throw new EvalError(method.toString() + " is private in this scope.", callerInfo, callStack);
            }
            return method.invoke(args, krineBasicInterpreter, callStack, callerInfo);
        }

        throw new InterpreterException("Method " + methodName + "() was not declared in this scope.");
    }

    // end compound name routines

    public String toString() {
        return value;
    }

}

