package com.krine.lang;

import com.krine.debugger.IDebugger;
import com.krine.lang.ast.*;
import com.krine.lang.classpath.KrineClassManager;
import com.krine.lang.io.SystemIOBridge;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.utils.CallStack;
import krine.module.Module;

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Krine script krineBasicInterpreter.
 * <p>
 * An instance of KrineInterpreter can be used to source scripts and evaluate
 * statements or expressions.
 * <p>
 * In the above examples we showed a single krineBasicInterpreter instance, however
 * you may wish to use many instances, depending on the application and how
 * you structure your scripts.  KrineInterpreter instances are very light weight
 * to create, however if you are going to execute the same script repeatedly
 * and require maximum performance you should consider scripting the code as
 * a method and invoking the scripted method each time on the same krineBasicInterpreter
 * instance (using eval()).
 * <p>
 * <p>
 * See the Krine User's Manual for more information.
 */
public class KrineBasicInterpreter
        implements Runnable, SystemIOBridge, Serializable {
    private static final This SYSTEM_OBJECT = This.getThis(new NameSpace(null, null, "krine.system"), null);
    /* --- Begin static members --- */
    /*
        Debug utils are static so that they are reachable by code that doesn't
        necessarily have an krineBasicInterpreter reference (e.g. tracing in utils).
        In the future we may want to allow debug/trace to be turned on on
        a per krineBasicInterpreter basis, in which case we'll need to use the parent
        reference in some way to determine the scope of the command that
        turns it on or off.
    */
    public static boolean DEBUG, TRACE, LOCAL_SCOPING;
    // This should be per instance
    private transient static PrintStream debugStream;

    static {
        staticInit();
    }

    /**
     * Strict Java mode
     *
     * @see #setStrictJava(boolean)
     */
    private boolean strictJava = false;

    /**
     * Whether we can use Java classes in Krine.
     *
     * @see #setAllowJavaClass(boolean)
     */
    private boolean allowJavaClass = true;

	/* --- End static members --- */

	/* --- Instance data --- */

    private transient Parser parser;
    private NameSpace globalNameSpace;
    private transient Reader in;
    private transient PrintStream out;
    private transient PrintStream err;
    private SystemIOBridge console;

    /**
     * If this interpreter is a child of another, the parent
     */
    private KrineBasicInterpreter parent;

    private boolean evalOnly;        // KrineInterpreter has no input stream, use eval() only

    private IDebugger debugger;

    private Map<String, Module> modules;

	/* --- End instance data --- */

    /**
     * The main constructor.
     * All constructors should now pass through here.
     *
     * @param namespace If nameSpace is non-null then this krineBasicInterpreter's
     *                  root nameSpace will be set to the one provided.  If it is null a new
     *                  one will be created for it.
     * @param parent    The parent krineBasicInterpreter if this krineBasicInterpreter is a child
     *                  of another.  May be null.  Children share a KrineClassManager with
     *                  their parent instance.
     */
    public KrineBasicInterpreter(
            Reader in, PrintStream out, PrintStream err,
            NameSpace namespace,
            KrineBasicInterpreter parent) {
        parser = new Parser(in);
        long t1 = 0;
        if (KrineBasicInterpreter.DEBUG) {
            t1 = System.currentTimeMillis();
        }
        this.in = in;
        this.out = out;
        this.err = err;
        debugStream = err;
        this.parent = parent;
        if (parent != null)
            setStrictJava(parent.isStrictJava());

        KrineClassManager dcm = KrineClassManager.createClassManager(this);
        if (namespace == null) {
            globalNameSpace = new NameSpace(dcm, "global");
            initRootSystemObject();
        } else {
            globalNameSpace = namespace;
            try {
                if (!(globalNameSpace.getVariable("krine") instanceof This)) {
                    initRootSystemObject();
                }
            } catch (final UtilEvalException e) {
                throw new IllegalStateException(e);
            }
        }

        // now done in NameSpace automatically when root
        // The classes which are imported by default
        // globalNameSpace.importDefaultPackages();

        if (KrineBasicInterpreter.DEBUG) {
            long t2 = System.currentTimeMillis();
            KrineBasicInterpreter.debug("Time to initialize KrineInterpreter: " + (t2 - t1));
        }
    }

    public KrineBasicInterpreter(
            Reader in, PrintStream out, PrintStream err,
            NameSpace namespace) {
        this(in, out, err, namespace, null);
    }

    public KrineBasicInterpreter(
            Reader in, PrintStream out, PrintStream err) {
        this(in, out, err, null);
    }

    /**
     * Construct a new interactive krineBasicInterpreter attached to the specified
     * console using the specified parent nameSpace.
     */
    public KrineBasicInterpreter(SystemIOBridge console, NameSpace globalNameSpace) {

        this(console.getIn(), console.getOut(), console.getErr(),
                globalNameSpace);

        setSystemIOBridge(console);
    }

    /**
     * Construct a new interactive krineBasicInterpreter attached to the specified
     * console.
     */
    public KrineBasicInterpreter(SystemIOBridge console) {
        this(console, null);
    }

    /**
     * Create an krineBasicInterpreter for evaluation only.
     */
    public KrineBasicInterpreter() {
        this(new StringReader(""),
                System.out, System.err, null);
        evalOnly = true;
        setUnchecked("krine.evalOnly", new Primitive(true));
    }

    // End constructors

    public static void invokeMain(Class clazz, String[] args)
            throws Exception {
        Method main = Reflect.resolveJavaMethod(
                null/*KrineClassManager*/, clazz, "main",
                new Class[]{String[].class}, true/*onlyStatic*/);
        if (main != null) {

            main.invoke(null, new Object[]{args});
        }
    }

    /**
     * Print a debugStream message on debugStream stream associated with this krineBasicInterpreter
     * only if debugging is turned on.
     */
    public static void debug(String s) {
        if (DEBUG) {
            debugStream.println(" *** <Debug>: " + s);
        }
    }

    public static void redirectOutputToFile(String filename) {
        try {
            PrintStream pout = new PrintStream(
                    new FileOutputStream(filename));
            System.setOut(pout);
            System.setErr(pout);
        } catch (IOException e) {
            System.err.println("Can't redirect output to file: " + filename);
        }
    }

    private static void staticInit() {
        try {
            debugStream = System.err;
            DEBUG = Boolean.getBoolean("debug");
            TRACE = Boolean.getBoolean("trace");
            LOCAL_SCOPING = Boolean.getBoolean("local_scoping");
            String outFile = System.getProperty("outfile");
            if (outFile != null)
                redirectOutputToFile(outFile);
        } catch (SecurityException e) {
            System.err.println("Could not init static(level 1):" + e);
        } catch (Exception e) {
            System.err.println("Could not init static(level 2):" + e);
        } catch (Throwable e) {
            System.err.println("Could not init static(level 3):" + e);
        }
    }

    /**
     * Attach a console
     * Note: this method is incomplete.
     */
    public void setSystemIOBridge(SystemIOBridge console) {
        this.console = console;
        setUnchecked("krine.io", console);

        setOut(console.getOut());
        setErr(console.getErr());
    }


    // begin source and eval

    private void initRootSystemObject() {
        KrineClassManager dcm = getClassManager();

        setUnchecked("krine", new NameSpace(dcm, "Krine_Language").getThis(this));
        setUnchecked("krine.system", SYSTEM_OBJECT);

        // save current working directory for dynamic loading
        try {
            setUnchecked("krine.cwd", System.getProperty("user.dir"));
        } catch (SecurityException e) {
            setUnchecked("krine.cwd", ".");
        }

        setUnchecked("krine.evalOnly", new Primitive(evalOnly));
    }

    /**
     * Get the global nameSpace of this krineBasicInterpreter.
     * <p>
     * <p>
     * Note: This is here for completeness.  If you're using this a lot
     * it may be an indication that you are doing more work than you have
     * to.  For example, caching the krineBasicInterpreter instance rather than the
     * nameSpace should not add a significant overhead.  No state other than
     * the debugStream status is stored in the krineBasicInterpreter.
     * <p>
     * <p>
     * All features of the nameSpace can also be accessed using the
     * krineBasicInterpreter via eval() and the script variable 'this.nameSpace'
     * (or global.nameSpace as necessary).
     */
    public NameSpace getNameSpace() {
        return globalNameSpace;
    }

    /**
     * @deprecated Since Krine 1.1
     * Set the global nameSpace for this krineBasicInterpreter.
     * <p>
     * <p>
     * Note: This is here for completeness.  If you're using this a lot
     * it may be an indication that you are doing more work than you have
     * to.  For example, caching the krineBasicInterpreter instance rather than the
     * nameSpace should not add a significant overhead.  No state other
     * than the debugStream status is stored in the krineBasicInterpreter.
     * <p>
     * <p>
     * All features of the nameSpace can also be accessed using the
     * krineBasicInterpreter via eval() and the script variable 'this.nameSpace'
     * (or global.nameSpace as necessary).
     */
    public void setNameSpace(NameSpace globalNameSpace) {
        this.globalNameSpace = globalNameSpace;
    }

    /**
     * Read text from fileName and eval it.
     */
    public Object source(String filename, NameSpace nameSpace)
            throws IOException, EvalError {
        File file = convertToPath(filename);
        if (KrineBasicInterpreter.DEBUG) debug("Sourcing file: " + file);
        try (Reader sourceIn = new BufferedReader(new FileReader(file))) {
            return eval(sourceIn, nameSpace, filename);
        }
    }

    /**
     * Read text from fileName and eval it.
     * Convenience method.  Use the global nameSpace.
     */
    public Object source(String filename)
            throws IOException, EvalError {
        return source(filename, globalNameSpace);
    }

    /**
     * Spawn a non-interactive local krineBasicInterpreter to evaluate text in the
     * specified nameSpace.
     * <p>
     * Return value is the evaluated object (or corresponding primitive
     * wrapper).
     *
     * @param sourceFileInfo is for information purposes only.  It is used to
     *                       display error messages (and in the future may be made available to
     *                       the script).
     * @throws EvalError            on script problems
     * @throws KrineTargetException on unhandled exceptions from the script
     */
    /*
        Note: we need a form of eval that passes the callStack through...
	*/
    /*
    Can't this be combined with run() ?
	run seems to have stuff in it for interactive vs. non-interactive...
	compare them side by side and see what they do differently, aside from the
	exception handling.
	*/
    public Object eval(
            Reader in, NameSpace nameSpace, String sourceFileInfo
            /*, CallStack callStack */)
            throws EvalError {
        Object retVal = null;
        if (KrineBasicInterpreter.DEBUG) debug("eval: nameSpace = " + nameSpace);

		/*
            Create non-interactive local krineBasicInterpreter for this nameSpace
			with source from the input stream and out/err same as
			this krineBasicInterpreter.
		*/
        KrineBasicInterpreter localKrineBasicInterpreter =
                new KrineBasicInterpreter(
                        in, out, err, nameSpace, this);

        CallStack callStack = new CallStack(nameSpace);

        SimpleNode node = null;
        boolean eof = false;
        while (!eof) {
            try {
                eof = localKrineBasicInterpreter.Line();
                if (localKrineBasicInterpreter.get_jjtree().nodeArity() > 0) {
                    if (node != null)
                        node.lastToken.next = null;  // prevent OutOfMemoryError

                    node = (SimpleNode) localKrineBasicInterpreter.get_jjtree().rootNode();
                    // nodes remember from where they were sourced
                    node.setSourceFile(sourceFileInfo);

                    // bind debugger if we are debugging
                    if (debugger != null) {
                        bindDebugger(node, debugger);
                        debugger.onProgramStarted(callStack);
                    }

                    // evaluate the program
                    retVal = node.eval(callStack, localKrineBasicInterpreter);

                    // sanity check during development
                    if (callStack.depth() > 1)
                        throw new InterpreterException(
                                "CallStack growing: " + callStack);

                    if (retVal instanceof ReturnControl) {
                        retVal = ((ReturnControl) retVal).value;
                        break; // non-interactive, return control now
                    }
                }
            } catch (ParseException e) {
                /*
                throw new EvalError(
					"Sourced file: "+sourceFileInfo+" parser Error: "
					+ e.getMessage( DEBUG ), node, callStack );
				*/
                if (DEBUG)
                    // show extra "expecting..." info
                    error(e.getMessage(DEBUG));

                // add the source file info and throw again
                e.setErrorSourceFile(sourceFileInfo);
                throw e;

            } catch (InterpreterException e) {
                e.printStackTrace();
                throw new EvalError(
                        "Sourced file: " + sourceFileInfo + " internal Error: "
                                + e.getMessage(), node, callStack);
            } catch (KrineTargetException e) {
                // fail-safe, set the Line as the origin of the error.
                if (e.getNode() == null)
                    e.setNode(node);
                e.reThrow("Sourced file: " + sourceFileInfo);
            } catch (EvalError e) {
                if (DEBUG)
                    e.printStackTrace();
                // fail-safe, set the Line as the origin of the error.
                if (e.getNode() == null)
                    e.setNode(node);
                e.reThrow("Sourced file: " + sourceFileInfo);
            } catch (Exception e) {
                if (DEBUG)
                    e.printStackTrace();
                throw new EvalError(
                        "Sourced file: " + sourceFileInfo + e.getClass().getSimpleName()
                                + e.getMessage(), node, callStack, e);
            } catch (KrineTokenException e) {
                throw new EvalError(
                        "Sourced file: " + sourceFileInfo + " Token Parsing Error: "
                                + e.getMessage(), node, callStack, e);
            } finally {
                localKrineBasicInterpreter.get_jjtree().reset();

                // re-init the callStack
                if (callStack.depth() > 1) {
                    callStack.clear();
                    callStack.push(nameSpace);
                }
            }
        }

        if (debugger != null) {
            debugger.onProgramExited(retVal);
        }
        return Primitive.unwrap(retVal);
    }

    /**
     * Bind debugger for program
     *
     * @param node     node to debugStream
     * @param debugger debugger
     */
    private void bindDebugger(SimpleNode node, IDebugger debugger) {
        SimpleNode child;
        Set<Integer> breakPoints = debugger.getFileBreakPoints(node.getSourceFile());

        if (breakPoints == null) {
            return;
        }

        for (int i = 0; i < node.jjtGetNumChildren(); ++i) {
            child = node.getChild(i);

            if (breakPoints.contains(child.getLineNumber()) && !child.getText().isEmpty()) {
                child.setDebugger(debugger);
                debug("Set breakpoint at " + child.getSourceFile() + ":" + child.getLineNumber() + ": " + child.getText());
                // TODO Merge the same statement
                continue;
            }
            bindDebugger(child, debugger);
        }
    }

    /**
     * Evaluate the InputStream in this krineBasicInterpreter's global nameSpace.
     */
    public Object eval(Reader in) throws EvalError {
        return eval(in, globalNameSpace, "eval stream");
    }

    // end source and eval

    /**
     * Evaluate the string in this krineBasicInterpreter's global nameSpace.
     */
    public Object eval(String statements) throws EvalError {
        if (KrineBasicInterpreter.DEBUG) debug("eval(String): " + statements);
        return eval(statements, globalNameSpace);
    }

    // SystemIOBridge
    // The krineBasicInterpreter reflexively implements the console interface that it
    // uses.  Should clean this up by using an inner class to implement the
    // console for us.

    /**
     * Evaluate the string in the specified nameSpace.
     */
    public Object eval(String statements, NameSpace nameSpace)
            throws EvalError {

        String s = (statements.endsWith(";") ? statements : statements + ";");
        return eval(
                new StringReader(s), nameSpace,
                "inline evaluation of: ``" + showEvalString(s) + "''");
    }

    private String showEvalString(String s) {
        s = s.replace('\n', ' ');
        s = s.replace('\r', ' ');
        if (s.length() > 80)
            s = s.substring(0, 80) + " . . . ";
        return s;
    }

    /**
     * Print an error message in a standard format on the output stream
     * associated with this krineBasicInterpreter. On the GUI console this will appear
     * in red, etc.
     */
    public final void error(Object o) {
        if (console != null)
            console.error(" *** <Error>: " + o + "\n");
        else {
            err.println(" *** <Error>: " + o);
            err.flush();
        }
    }

    /**
     * Get the input stream associated with this krineBasicInterpreter.
     * This may be be stdin or the GUI console.
     */
    public Reader getIn() {
        return in;
    }

    /**
     * Get the output stream associated with this krineBasicInterpreter.
     * This may be be stdout or the GUI console.
     */
    public PrintStream getOut() {
        return out;
    }

    // End SystemIOBridge

    public void setOut(PrintStream out) {
        this.out = out;
    }

	/* 
        Primary krineBasicInterpreter set and get variable methods
		Note: These are squelching errors... should they?
	*/

    /**
     * Get the error output stream associated with this krineBasicInterpreter.
     * This may be be stderr or the GUI console.
     */
    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public final void println(Object o) {
        print(String.valueOf(o) + "\n");
    }

    public final void print(Object o) {
        if (console != null) {
            console.print(o);
        } else {
            out.print(o);
            out.flush();
        }
    }

    /**
     * Get the value of the name.
     * name may be any value. e.g. a variable or field
     */
    public Object get(String name) throws EvalError {
        try {
            Object ret = globalNameSpace.get(name, this);
            return Primitive.unwrap(ret);
        } catch (UtilEvalException e) {
            throw e.toEvalError(SimpleNode.JAVA_CODE, new CallStack());
        }
    }

    /**
     * Unchecked get for internal use
     */
    private Object getUnchecked(String name) {
        try {
            return get(name);
        } catch (EvalError e) {
            throw new InterpreterException("set: " + e);
        }
    }

    /**
     * Assign the value to the name.
     * name may evaluate to anything assignable. e.g. a variable or field.
     */
    public void set(String name, Object value)
            throws EvalError {
        // map null to Primitive.NULL coming in...
        if (value == null)
            value = Primitive.NULL;

        CallStack callStack = new CallStack();
        try {
            if (Name.isCompound(name)) {
                LeftValue lhs = globalNameSpace.getNameResolver(name).toLeftValue(
                        callStack, this);
                lhs.assign(value, false);
            } else {
                // optimization for common case
                globalNameSpace.setVariable(name, value, false);
            }
        } catch (UtilEvalException e) {
            throw e.toEvalError(SimpleNode.JAVA_CODE, callStack);
        }
    }

    /**
     * Unchecked set for internal use
     */
    public void setUnchecked(String name, Object value) {
        try {
            set(name, value);
        } catch (EvalError e) {
            throw new InterpreterException("set: " + e);
        }
    }

    public void set(String name, long value) throws EvalError {
        set(name, new Primitive(value));
    }

    public void set(String name, int value) throws EvalError {
        set(name, new Primitive(value));
    }

    // end primary set and get methods

    public void set(String name, double value) throws EvalError {
        set(name, new Primitive(value));
    }

	/*	Methods for interacting with Parser */

    public void set(String name, float value) throws EvalError {
        set(name, new Primitive(value));
    }

    public void set(String name, boolean value) throws EvalError {
        set(name, new Primitive(value));
    }

    /**
     * Un-assign the variable name.
     * Name should evaluate to a variable.
     */
    public void unset(String name)
            throws EvalError {
        /*
			We jump through some hoops here to handle arbitrary cases like
			unset("krine.foo");
		*/
        CallStack callStack = new CallStack();
        try {
            LeftValue lhs = globalNameSpace.getNameResolver(name).toLeftValue(
                    callStack, this);

            if (lhs.type != LeftValue.VARIABLE)
                throw new EvalError("Can't unset, not a variable: " + name,
                        SimpleNode.JAVA_CODE, new CallStack());

            //lhs.assign( null, false );
            lhs.nameSpace.unsetVariable(name);
        } catch (UtilEvalException e) {
            throw new EvalError(e.getMessage(),
                    SimpleNode.JAVA_CODE, new CallStack());
        }
    }

	/*	End methods for interacting with Parser */

    /**
     * Get a reference to the krineBasicInterpreter (global nameSpace), cast
     * to the specified interface type.  Assuming the appropriate
     * methods of the interface are defined in the krineBasicInterpreter, then you may
     * use this interface from Java, just like any other Java object.
     *
     * @throws EvalError if the interface cannot be generated because the
     *                   version of Java does not support the proxy mechanism.
     */
    public Object asInterface(Class interfaceClass) throws EvalError {
        return globalNameSpace.getThis(this).getInterface(interfaceClass);
    }

    private JJTParserState get_jjtree() {
        return parser.jjtree;
    }

    private JavaCharStream get_jj_input_stream() {
        return parser.jj_input_stream;
    }

    private boolean Line() throws ParseException {
        return parser.Line();
    }

    /**
     * Localize a path to the file name based on the krine.cwd krineBasicInterpreter
     * working directory.
     */
    private File convertToPath(String fileName)
            throws IOException {
        File file = new File(fileName);

        // if relative, fix up to krine.cwd
        if (!file.isAbsolute()) {
            String cwd = (String) getUnchecked("krine.cwd");
            file = new File(cwd + File.separator + fileName);
        }

        // The canonical file name is also absolute.
        // No need for getAbsolutePath() here...
        return new File(file.getCanonicalPath());
    }

    /**
     * Set an external class loader to be used as the base classloader
     * for Krine.  The base classloader is used for all classloading
     * unless/until the addClasspath()/setClasspath()/reloadClasses()
     * commands are called to modify the krineBasicInterpreter's classpath.  At that
     * time the new paths /updated paths are added on top of the base
     * classloader.
     * <p>
     * <p>
     * Krine will use this at the same point it would otherwise use the
     * plain Class.forName().
     * i.e. if no explicit classpath management is done from the script
     * (addClassPath(), setClassPath(), reloadClasses()) then Krine will
     * only use the supplied classloader.  If additional classpath management
     * is done then Krine will perform that in addition to the supplied
     * external classloader.
     * However Krine is not currently able to reload
     * classes supplied through the external classloader.
     * <p>
     *
     * @see KrineClassManager#setClassLoader(ClassLoader)
     */
    public void setClassLoader(ClassLoader externalCL) {
        getClassManager().setClassLoader(externalCL);
    }

    /**
     * Get the class manager associated with this krineBasicInterpreter
     * (the KrineClassManager of this krineBasicInterpreter's global nameSpace).
     * This is primarily a convenience method.
     */
    public KrineClassManager getClassManager() {
        return getNameSpace().getClassManager();
    }

    /**
     * @return Strict Java
     * @see #setStrictJava(boolean)
     */
    public boolean isStrictJava() {
        return this.strictJava;
    }

    /**
     * Set strict Java mode on or off.
     * This mode attempts to make Krine syntax behave as Java
     * syntax, eliminating conveniences like loose variables, etc.
     * When enabled, variables are required to be declared or initialized
     * before use and method arguments are required to have types.
     * <p>
     * <p>
     * This mode will become more strict in a future release when
     * classes are interpreted and there is an alternative to scripting
     * objects as method closures.
     *
     * @param b Strict Java
     * @see #isStrictJava()
     */
    public void setStrictJava(boolean b) {
        this.strictJava = b;
    }

    /**
     * @return allow Java classes
     * @see #setAllowJavaClass(boolean)
     */
    public boolean isAllowJavaClass() {
        return allowJavaClass;
    }

    /**
     * Allow KrineInterpreter to load Java classes or not.
     *
     * @param b Allow Java classes
     * @see #isAllowJavaClass()
     */
    public void setAllowJavaClass(boolean b) {
        this.allowJavaClass = b;
    }

    public void setDebugger(IDebugger debugger) {
        this.debugger = debugger;
    }

    public Module getImportedModule(String moduleName) {
        if (modules == null) {
            return null;
        }

        if (modules.containsKey(moduleName)) {
            return modules.get(moduleName);
        }
        return null;
    }

    /**
     * Import an module.
     *
     * @param module Module
     * @see Module#export(This, String)
     */
    public void importModule(Module module) {
        if (modules == null) {
            modules = new HashMap<>();
        }

        if (modules.containsKey(module.getName())) {
            return;
        }
        modules.put(module.getName(), module);
    }

    public void importPackageAsModule(KrineBasicInterpreter interpreter, String name) {
        Module wrap = Module.wrapJavaPackage(interpreter, name);
        if (wrap != null) {
            importModule(wrap);
        }
    }

    /**
     * Get the parent KrineInterpreter of this krineBasicInterpreter, if any.
     * Currently this relationship implies the following:
     * 1) Parent and child share a KrineClassManager
     * 2) Children indicate the parent's source file information in error
     * reporting.
     * When created as part of a source() / eval() the child also shares
     * the parent's nameSpace.  But that is not necessary in general.
     */
    public KrineBasicInterpreter getParent() {
        return parent;
    }

    public NameSpace getGlobalNameSpace() {
        return globalNameSpace;
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("Interactive Krine has been removed since Krine 1.1");
    }
}
