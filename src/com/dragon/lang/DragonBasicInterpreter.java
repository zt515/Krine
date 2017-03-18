package com.dragon.lang;

import com.dragon.lang.ast.*;
import com.dragon.lang.classpath.DragonClassManager;
import com.dragon.lang.debugger.DragonDebugger;
import com.dragon.lang.io.SystemIOBridge;
import com.dragon.lang.reflect.Reflect;
import com.dragon.lang.utils.CallStack;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * The Dragon script dragonBasicInterpreter.
 * <p>
 * An instance of DragonInterpreter can be used to source scripts and evaluate
 * statements or expressions.
 * <p>
 * Here are some examples:
 * <p>
 * <p><blockquote><pre>
 * Interpeter dragon = new DragonInterpreter();
 * <p>
 * // Evaluate statements and expressions
 * dragon.eval("foo=Math.sin(0.5)");
 * dragon.eval("bar=foo*5; bar=Math.cos(bar);");
 * dragon.eval("for(i=0; i<10; i++) { print(\"hello\"); }");
 * // same as above using java syntax and apis only
 * dragon.eval("for(int i=0; i<10; i++) { System.out.println(\"hello\"); }");
 * <p>
 * // Source from files or streams
 * dragon.source("myscript.dragon");  // or dragon.eval("source(\"myscript.dragon\")");
 * <p>
 * // Use set() and get() to pass objects in and out of variables
 * dragon.set( "date", new Date() );
 * Date date = (Date)dragon.get( "date" );
 * // This would also work:
 * Date date = (Date)dragon.eval( "date" );
 * <p>
 * dragon.eval("year = date.getYear()");
 * Integer year = (Integer)dragon.get("year");  // primitives use wrappers
 * <p>
 * </pre></blockquote>
 * <p>
 * In the above examples we showed a single dragonBasicInterpreter instance, however
 * you may wish to use many instances, depending on the application and how
 * you structure your scripts.  DragonInterpreter instances are very light weight
 * to create, however if you are going to execute the same script repeatedly
 * and require maximum performance you should consider scripting the code as
 * a method and invoking the scripted method each time on the same dragonBasicInterpreter
 * instance (using eval()).
 * <p>
 * <p>
 * See the Dragon User's Manual for more information.
 */
public class DragonBasicInterpreter
        implements Runnable, SystemIOBridge, Serializable {
    /* --- Begin static members --- */
    /*
        Debug utils are static so that they are reachable by code that doesn't
        necessarily have an dragonBasicInterpreter reference (e.g. tracing in utils).
        In the future we may want to allow debug/trace to be turned on on
        a per dragonBasicInterpreter basis, in which case we'll need to use the parent
        reference in some way to determine the scope of the command that
        turns it on or off.
    */
    public static boolean DEBUG, TRACE, LOCAL_SCOPING;

    // This should be per instance
    private transient static PrintStream debug;
    private static final This SYSTEM_OBJECT = This.getThis(new NameSpace(null, null, "dragon.system"), null);

    static {
        staticInit();
    }

    /**
     * Strict Java mode
     *
     * @see #setStrictJava(boolean)
     */
    private boolean strictJava = false;

	/* --- End static members --- */

	/* --- Instance data --- */

    private transient Parser parser;
    private NameSpace globalNameSpace;
    private transient Reader in;
    private transient PrintStream out;
    private transient PrintStream err;
    private SystemIOBridge console;

    /**
     * If this interpeter is a child of another, the parent
     */
    private DragonBasicInterpreter parent;

    /**
     * The name of the file or other source that this dragonBasicInterpreter is reading
     */
    private String sourceFileInfo;

    private boolean evalOnly;        // DragonInterpreter has no input stream, use eval() only

    private DragonDebugger debugger;

	/* --- End instance data --- */

    /**
     * The main constructor.
     * All constructors should now pass through here.
     *
     * @param namespace      If namespace is non-null then this dragonBasicInterpreter's
     *                       root namespace will be set to the one provided.  If it is null a new
     *                       one will be created for it.
     * @param parent         The parent dragonBasicInterpreter if this dragonBasicInterpreter is a child
     *                       of another.  May be null.  Children share a DragonClassManager with
     *                       their parent instance.
     * @param sourceFileInfo An informative string holding the filename
     *                       or other description of the source from which this dragonBasicInterpreter is
     *                       reading... used for debugging.  May be null.
     */
    public DragonBasicInterpreter(
            Reader in, PrintStream out, PrintStream err,
            NameSpace namespace,
            DragonBasicInterpreter parent, String sourceFileInfo) {
        parser = new Parser(in);
        long t1 = 0;
        if (DragonBasicInterpreter.DEBUG) {
            t1 = System.currentTimeMillis();
        }
        this.in = in;
        this.out = out;
        this.err = err;
        debug = err;
        this.parent = parent;
        if (parent != null)
            setStrictJava(parent.getStrictJava());
        this.sourceFileInfo = sourceFileInfo;

        DragonClassManager dcm = DragonClassManager.createClassManager(this);
        if (namespace == null) {
            globalNameSpace = new NameSpace(dcm, "global");
            initRootSystemObject();
        } else {
            globalNameSpace = namespace;
            try {
                if (!(globalNameSpace.getVariable("dragon") instanceof This)) {
                    initRootSystemObject();
                }
            } catch (final UtilEvalException e) {
                throw new IllegalStateException(e);
            }
        }

        // now done in NameSpace automatically when root
        // The classes which are imported by default
        //globalNameSpace.loadDefaultImports();

        if (DragonBasicInterpreter.DEBUG) {
            long t2 = System.currentTimeMillis();
            DragonBasicInterpreter.debug("Time to initialize DragonInterpreter: " + (t2 - t1));
        }
    }

    public DragonBasicInterpreter(
            Reader in, PrintStream out, PrintStream err,
            NameSpace namespace) {
        this(in, out, err, namespace, null, null);
    }

    public DragonBasicInterpreter(
            Reader in, PrintStream out, PrintStream err) {
        this(in, out, err, null);
    }

    /**
     * Construct a new interactive dragonBasicInterpreter attached to the specified
     * console using the specified parent namespace.
     */
    public DragonBasicInterpreter(SystemIOBridge console, NameSpace globalNameSpace) {

        this(console.getIn(), console.getOut(), console.getErr(),
                globalNameSpace);

        setSystemIOBridge(console);
    }

    /**
     * Construct a new interactive dragonBasicInterpreter attached to the specified
     * console.
     */
    public DragonBasicInterpreter(SystemIOBridge console) {
        this(console, null);
    }

    /**
     * Create an dragonBasicInterpreter for evaluation only.
     */
    public DragonBasicInterpreter() {
        this(new StringReader(""),
                System.out, System.err, null);
        evalOnly = true;
        setUnchecked("dragon.evalOnly", new Primitive(true));
    }

    // End constructors

    /**
     * Attach a console
     * Note: this method is incomplete.
     */
    public void setSystemIOBridge(SystemIOBridge console) {
        this.console = console;
        setUnchecked("dragon.io", console);

        setOut(console.getOut());
        setErr(console.getErr());
    }

    private void initRootSystemObject() {
        DragonClassManager dcm = getClassManager();

        setUnchecked("dragon", new NameSpace(dcm, "DragonInterpreter_Object").getThis(this));
        setUnchecked("dragon.system", SYSTEM_OBJECT);

        // save current working directory for dynamic loading
        try {
            setUnchecked("dragon.cwd", System.getProperty("user.dir"));
        } catch (SecurityException e) {
            setUnchecked("dragon.cwd", ".");
        }

        setUnchecked("dragon.evalOnly", new Primitive(evalOnly));
    }

    /**
     * @deprecated Since Dragon 1.1
     * Set the global namespace for this dragonBasicInterpreter.
     * <p>
     * <p>
     * Note: This is here for completeness.  If you're using this a lot
     * it may be an indication that you are doing more work than you have
     * to.  For example, caching the dragonBasicInterpreter instance rather than the
     * namespace should not add a significant overhead.  No state other
     * than the debug status is stored in the dragonBasicInterpreter.
     * <p>
     * <p>
     * All features of the namespace can also be accessed using the
     * dragonBasicInterpreter via eval() and the script variable 'this.namespace'
     * (or global.namespace as necessary).
     */
    public void setNameSpace(NameSpace globalNameSpace) {
        this.globalNameSpace = globalNameSpace;
    }

    /**
     * Get the global namespace of this dragonBasicInterpreter.
     * <p>
     * <p>
     * Note: This is here for completeness.  If you're using this a lot
     * it may be an indication that you are doing more work than you have
     * to.  For example, caching the dragonBasicInterpreter instance rather than the
     * namespace should not add a significant overhead.  No state other than
     * the debug status is stored in the dragonBasicInterpreter.
     * <p>
     * <p>
     * All features of the namespace can also be accessed using the
     * dragonBasicInterpreter via eval() and the script variable 'this.namespace'
     * (or global.namespace as necessary).
     */
    public NameSpace getNameSpace() {
        return globalNameSpace;
    }

    public static void invokeMain(Class clazz, String[] args)
            throws Exception {
        Method main = Reflect.resolveJavaMethod(
                null/*DragonClassManager*/, clazz, "main",
                new Class[]{String[].class}, true/*onlyStatic*/);
        if (main != null)
            main.invoke(null, new Object[]{args});
    }


    // begin source and eval

    /**
     * Read text from fileName and eval it.
     */
    public Object source(String filename, NameSpace nameSpace)
            throws IOException, EvalError {
        File file = convertToPath(filename);
        if (DragonBasicInterpreter.DEBUG) debug("Sourcing file: " + file);
        Reader sourceIn = new BufferedReader(new FileReader(file));
        try {
            return eval(sourceIn, nameSpace, filename);
        } finally {
            sourceIn.close();
        }
    }

    /**
     * Read text from fileName and eval it.
     * Convenience method.  Use the global namespace.
     */
    public Object source(String filename)
            throws IOException, EvalError {
        return source(filename, globalNameSpace);
    }

    /**
     * Spawn a non-interactive local dragonBasicInterpreter to evaluate text in the
     * specified namespace.
     * <p>
     * Return value is the evaluated object (or corresponding primitive
     * wrapper).
     *
     * @param sourceFileInfo is for information purposes only.  It is used to
     *                       display error messages (and in the future may be made available to
     *                       the script).
     * @throws EvalError             on script problems
     * @throws DragonTargetException on unhandled exceptions from the script
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
        if (DragonBasicInterpreter.DEBUG) debug("eval: nameSpace = " + nameSpace);

		/* 
			Create non-interactive local dragonBasicInterpreter for this namespace
			with source from the input stream and out/err same as 
			this dragonBasicInterpreter.
		*/
        DragonBasicInterpreter localDragonBasicInterpreter =
                new DragonBasicInterpreter(
                        in, out, err, nameSpace, this, sourceFileInfo);

        CallStack callstack = new CallStack(nameSpace);

        SimpleNode node = null;
        boolean eof = false;
        while (!eof) {
            try {
                eof = localDragonBasicInterpreter.Line();
                if (localDragonBasicInterpreter.get_jjtree().nodeArity() > 0) {
                    if (node != null)
                        node.lastToken.next = null;  // prevent OutOfMemoryError

                    node = (SimpleNode) localDragonBasicInterpreter.get_jjtree().rootNode();
                    // nodes remember from where they were sourced
                    node.setSourceFile(sourceFileInfo);

                    // bind debugger if we are debugging
                    if (debugger != null) {
                        bindDebugger(node, debugger);
                    }

                    // evaluate the program
                    retVal = node.eval(callstack, localDragonBasicInterpreter);

                    // sanity check during development
                    if (callstack.depth() > 1)
                        throw new InterpreterException(
                                "CallStack growing: " + callstack);

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
                                + e.getMessage(), node, callstack);
            } catch (DragonTargetException e) {
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
                        "Sourced file: " + sourceFileInfo + " unknown error: "
                                + e.getMessage(), node, callstack, e);
            } catch (DragonTokenException e) {
                throw new EvalError(
                        "Sourced file: " + sourceFileInfo + " Token Parsing Error: "
                                + e.getMessage(), node, callstack, e);
            } finally {
                localDragonBasicInterpreter.get_jjtree().reset();

                // re-init the callStack
                if (callstack.depth() > 1) {
                    callstack.clear();
                    callstack.push(nameSpace);
                }
            }
        }
        return Primitive.unwrap(retVal);
    }

    /**
     * Bind debugger for program
     *
     * @param node     node to debug
     * @param debugger debugger
     */
    private void bindDebugger(SimpleNode node, DragonDebugger debugger) {
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
                continue;
            }
            bindDebugger(child, debugger);
        }
    }

    /**
     * Evaluate the InputStream in this dragonBasicInterpreter's global namespace.
     */
    public Object eval(Reader in) throws EvalError {
        return eval(in, globalNameSpace, "eval stream");
    }

    /**
     * Evaluate the string in this dragonBasicInterpreter's global namespace.
     */
    public Object eval(String statements) throws EvalError {
        if (DragonBasicInterpreter.DEBUG) debug("eval(String): " + statements);
        return eval(statements, globalNameSpace);
    }

    /**
     * Evaluate the string in the specified namespace.
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

    // end source and eval

    /**
     * Print an error message in a standard format on the output stream
     * associated with this dragonBasicInterpreter. On the GUI console this will appear
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

    // SystemIOBridge
    // The dragonBasicInterpreter reflexively implements the console interface that it
    // uses.  Should clean this up by using an inner class to implement the
    // console for us.

    /**
     * Get the input stream associated with this dragonBasicInterpreter.
     * This may be be stdin or the GUI console.
     */
    public Reader getIn() {
        return in;
    }

    /**
     * Get the output stream associated with this dragonBasicInterpreter.
     * This may be be stdout or the GUI console.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Get the error output stream associated with this dragonBasicInterpreter.
     * This may be be stderr or the GUI console.
     */
    public PrintStream getErr() {
        return err;
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

    // End SystemIOBridge

    /**
     * Print a debug message on debug stream associated with this dragonBasicInterpreter
     * only if debugging is turned on.
     */
    public static void debug(String s) {
        if (DEBUG) {
            debug.println(" *** <Debug>: " + s);
        }
    }

	/* 
		Primary dragonBasicInterpreter set and get variable methods
		Note: These are squeltching errors... should they?
	*/

    /**
     * Get the value of the name.
     * name may be any value. e.g. a variable or field
     */
    public Object get(String name) throws EvalError {
        try {
            Object ret = globalNameSpace.get(name, this);
            return Primitive.unwrap(ret);
        } catch (UtilEvalException e) {
            throw e.toEvalError(SimpleNode.JAVACODE, new CallStack());
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

        CallStack callstack = new CallStack();
        try {
            if (Name.isCompound(name)) {
                LeftValue lhs = globalNameSpace.getNameResolver(name).toLeftValue(
                        callstack, this);
                lhs.assign(value, false);
            } else {
                // optimization for common case
                globalNameSpace.setVariable(name, value, false);
            }
        } catch (UtilEvalException e) {
            throw e.toEvalError(SimpleNode.JAVACODE, callstack);
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

    public void set(String name, double value) throws EvalError {
        set(name, new Primitive(value));
    }

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
			unset("dragon.foo");
		*/
        CallStack callStack = new CallStack();
        try {
            LeftValue lhs = globalNameSpace.getNameResolver(name).toLeftValue(
                    callStack, this);

            if (lhs.type != LeftValue.VARIABLE)
                throw new EvalError("Can't unset, not a variable: " + name,
                        SimpleNode.JAVACODE, new CallStack());

            //lhs.assign( null, false );
            lhs.nameSpace.unsetVariable(name);
        } catch (UtilEvalException e) {
            throw new EvalError(e.getMessage(),
                    SimpleNode.JAVACODE, new CallStack());
        }
    }

    // end primary set and get methods

    /**
     * Get a reference to the dragonBasicInterpreter (global namespace), cast
     * to the specified interface type.  Assuming the appropriate
     * methods of the interface are defined in the dragonBasicInterpreter, then you may
     * use this interface from Java, just like any other Java object.
     *
     * @throws EvalError if the interface cannot be generated because the
     *                   version of Java does not support the proxy mechanism.
     */
    public Object asInterface(Class interfaceClass) throws EvalError {
        return globalNameSpace.getThis(this).getInterface(interfaceClass);
    }

	/*	Methods for interacting with Parser */

    private JJTParserState get_jjtree() {
        return parser.jjtree;
    }

    private JavaCharStream get_jj_input_stream() {
        return parser.jj_input_stream;
    }

    private boolean Line() throws ParseException {
        return parser.Line();
    }

	/*	End methods for interacting with Parser */

    /**
     * Localize a path to the file name based on the dragon.cwd dragonBasicInterpreter
     * working directory.
     */
    private File convertToPath(String fileName)
            throws IOException {
        File file = new File(fileName);

        // if relative, fix up to dragon.cwd
        if (!file.isAbsolute()) {
            String cwd = (String) getUnchecked("dragon.cwd");
            file = new File(cwd + File.separator + fileName);
        }

        // The canonical file name is also absolute.
        // No need for getAbsolutePath() here...
        return new File(file.getCanonicalPath());
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

    /**
     * Set an external class loader to be used as the base classloader
     * for Dragon.  The base classloader is used for all classloading
     * unless/until the addClasspath()/setClasspath()/reloadClasses()
     * commands are called to modify the dragonBasicInterpreter's classpath.  At that
     * time the new paths /updated paths are added on top of the base
     * classloader.
     * <p>
     * <p>
     * Dragon will use this at the same point it would otherwise use the
     * plain Class.forName().
     * i.e. if no explicit classpath management is done from the script
     * (addClassPath(), setClassPath(), reloadClasses()) then Dragon will
     * only use the supplied classloader.  If additional classpath management
     * is done then Dragon will perform that in addition to the supplied
     * external classloader.
     * However Dragon is not currently able to reload
     * classes supplied through the external classloader.
     * <p>
     *
     * @see DragonClassManager#setClassLoader(ClassLoader)
     */
    public void setClassLoader(ClassLoader externalCL) {
        getClassManager().setClassLoader(externalCL);
    }

    /**
     * Get the class manager associated with this dragonBasicInterpreter
     * (the DragonClassManager of this dragonBasicInterpreter's global namespace).
     * This is primarily a convenience method.
     */
    public DragonClassManager getClassManager() {
        return getNameSpace().getClassManager();
    }

    /**
     * Set strict Java mode on or off.
     * This mode attempts to make Dragon syntax behave as Java
     * syntax, eliminating conveniences like loose variables, etc.
     * When enabled, variables are required to be declared or initialized
     * before use and method arguments are reqired to have types.
     * <p>
     * <p>
     * This mode will become more strict in a future release when
     * classes are interpreted and there is an alternative to scripting
     * objects as method closures.
     */
    public void setStrictJava(boolean b) {
        this.strictJava = b;
    }

    /**
     * @see #setStrictJava(boolean)
     */
    public boolean getStrictJava() {
        return this.strictJava;
    }

    public void setDebugger(DragonDebugger debugger) {
        this.debugger = debugger;
    }

    private static void staticInit() {
        try {
            debug = System.err;
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
     * Get the parent DragonInterpreter of this dragonBasicInterpreter, if any.
     * Currently this relationship implies the following:
     * 1) Parent and child share a DragonClassManager
     * 2) Children indicate the parent's source file information in error
     * reporting.
     * When created as part of a source() / eval() the child also shares
     * the parent's namespace.  But that is not necessary in general.
     */
    public DragonBasicInterpreter getParent() {
        return parent;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public NameSpace getGlobalNameSpace() {
        return globalNameSpace;
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("Interactive Dragon has been removed since Dragon 1.1");
    }
}