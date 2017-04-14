package krine.module;

import com.krine.api.annotations.KrineAPI;
import com.krine.kar.KarEntry;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.ast.EvalError;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.This;
import com.krine.lang.utils.Capabilities;
import com.krine.lang.utils.LazySingleton;
import krine.core.Core;
import krine.core.KRuntimeException;

import java.io.File;

/**
 * This class provides Krine the module system.
 *
 * @author kiva
 * @date 2017/4/11
 */
@KrineAPI
@SuppressWarnings("unused")
public final class Module {
    /**
     * Save all Module path.
     * We only need it when Module.load called.
     */
    private static final LazySingleton<ModulePath> MODULE_PATH = new LazySingleton<ModulePath>() {
        @Override
        public ModulePath onCreate() {
            ModulePath mp = new ModulePath();
            String pathFromEnv = Core.getEnv("MODULEPATH");
            if (pathFromEnv != null) {
                for (String path : pathFromEnv.split(Capabilities.envSeparator())) {
                    mp.addModuleSearchPath(new File(path));
                }
            }
            return mp;
        }
    };
    private This moduleThis;
    private String name;

    private Module(String name, This aThis) {
        this.name = name;
        this.moduleThis = aThis;
    }

    /**
     * Add path in which module file located.
     *
     * @param path Path contains module file.
     * @see Module#addModuleSearchPath(File)
     */
    public static void addModuleSearchPath(String path) {
        Module.addModuleSearchPath(new File(path));
    }

    /**
     * Add path or .kar file in which module file located.
     *
     * @param file Path contains module file.
     * @see Module#addModuleSearchPath(String)
     */
    public static void addModuleSearchPath(File file) {
        MODULE_PATH.get().addModuleSearchPath(file);
    }

    /**
     * Get pure file name without path and suffix.
     * eg. /base/app.k => app
     *
     * @param file File path
     * @return Pure file name.
     */
    private static String parseModuleName(String file) {
        file = new File(file).getName();
        int index = file.lastIndexOf(".");
        return index == -1 ? file : file.substring(0, index);
    }

    /**
     * Create a new NameSpace for modules.
     *
     * @param interpreter Krine instance.
     * @param moduleName  Module name.
     * @return NameSpace which is ready to hold the Module's everything.
     */
    private static NameSpace createNameSpaceForModule(KrineBasicInterpreter interpreter, String moduleName) {
        return new NameSpace(interpreter.getGlobalNameSpace(), "Module_" + moduleName);
    }

    /**
     * Search ModulePath to find source file and load it as a module.
     *
     * @param aThis      Current scope.
     * @param moduleName File name, not file path.
     * @return Module instance
     * @see Module#loadModuleFromFile(This, String)
     */
    private static Module loadModuleFromModulePath(This aThis, String moduleName) {
        ModulePath modulePath = MODULE_PATH.get();

        // try .kar first
        KarEntry entry = modulePath.searchPackedModule(moduleName);
        if (entry != null) {
            // module is located in kar files.
            return loadModuleFromKarEntry(aThis, entry);
        }

        // try disk
        String result = modulePath.searchModuleOnDisk(moduleName);
        if (result != null) {
            // module is located on disk.
            return loadModuleFromFile(aThis, result);
        }

        return null;
    }

    /**
     * Load a KarEntry as a module.
     *
     * @param aThis Current scope.
     * @param entry Module entry.
     * @return Module instance
     */
    private static Module loadModuleFromKarEntry(This aThis, KarEntry entry) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);
        if (interpreter == null) {
            return null;
        }

        String content = entry.getStringContent();
        if (content == null) {
            return null;
        }

        String moduleName = parseModuleName(entry.getEntryName());
        NameSpace ns = createNameSpaceForModule(interpreter, moduleName);

        try {
            interpreter.eval(content, ns);
        } catch (EvalError e) {
            return null;
        }

        Module mod = new Module(moduleName, This.getThis(ns, interpreter));
        interpreter.importModule(mod);
        return mod;
    }

    /**
     * Load a file as a module.
     * This method is a wrap method of Core.load() and Module.loadModuleFromImported()
     *
     * @param aThis    Current scope.
     * @param filePath File name, not file path.
     * @return Module instance
     */
    private static Module loadModuleFromFile(This aThis, String filePath) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);
        if (interpreter == null) {
            return null;
        }

        String moduleName = parseModuleName(filePath);
        NameSpace ns = createNameSpaceForModule(interpreter, moduleName);

        if (!Core.load(aThis, filePath, ns)) {
            return null;
        }

        Module mod = new Module(moduleName, This.getThis(ns, interpreter));
        interpreter.importModule(mod);
        return mod;
    }

    /**
     * Get a imported module by given name.
     *
     * @param aThis      Current scope.
     * @param moduleName Module name
     * @return Module instance.
     */
    private static Module loadModuleFromImported(This aThis, String moduleName) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);

        if (interpreter == null) {
            return null;
        }

        return interpreter.getImportedModule(moduleName);
    }

    /**
     * Get the loaded module by given name.
     *
     * @param aThis      Current scope.
     * @param moduleName Module name
     * @return Module instance
     * @throws ModuleNotFoundException When module not found.
     */
    public static This load(This aThis, String moduleName) throws ModuleNotFoundException {
        // Load from imported module list
        Module mod = loadModuleFromImported(aThis, moduleName);

        // If module is not loaded,
        // try to load from ModulePath
        if (mod == null) {
            mod = loadModuleFromModulePath(aThis, moduleName);
        }

        if (mod == null) {
            throw new ModuleNotFoundException(moduleName, MODULE_PATH.get());
        }

        return mod.getThis();
    }

    /**
     * Export aThis as a module.
     *
     * @param aThis      This object to be set as a module
     * @param moduleName Module name
     * @throws KRuntimeException When cannot obtain the interpreter.
     */
    public static void export(This aThis, String moduleName) throws KRuntimeException {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);

        if (interpreter == null) {
            throw new KRuntimeException("Cannot get krine instance.");
        }

        Module mod = new Module(moduleName, aThis);
        interpreter.importModule(mod);
    }

    /**
     * Wrap a Java package to Module.
     * We can load packages into NameSpaces
     * and find classes more quickly.
     *
     * @param interpreter Context info
     * @param packageName Java package name
     * @return Module instance of warped Java package.
     */
    public static Module wrapJavaPackage(KrineBasicInterpreter interpreter, String packageName) {
        NameSpace ns = new NameSpace((NameSpace) null, "JavaPackage_" + packageName);
        ns.clearWithCoreImports();
        ns.importPackage(packageName);

        return new Module(packageName, ns.getThis(interpreter));
    }

    /**
     * @return Name of this module.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the module object.
     *
     * @return Module object
     * @see This#getThis(NameSpace, KrineBasicInterpreter)
     * @see NameSpace#getThis(KrineBasicInterpreter)
     */
    public This getThis() {
        return moduleThis;
    }
}

