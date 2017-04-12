package krine.module;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.KrineBasicInterpreter;
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
                    mp.addPath(path);
                }
            }
            return mp;
        }
    };

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
     * Add path in which module file located.
     *
     * @param file Path contains module file.
     * @see Module#addModuleSearchPath(String)
     */
    public static void addModuleSearchPath(File file) {
        if (file.isDirectory() && file.canRead()) {
            MODULE_PATH.get().addPath(file.getAbsolutePath());
        }
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
     * Search ModulePath to find source file and load it as a module.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param moduleName File name, not file path.
     * @return Module instance
     * @see Module#loadModuleFromFile(This, String)
     */
    private static Module loadModuleFromModulePath(This aThis, String moduleName) {
        // TODO: More suffix names.
        String filePath = MODULE_PATH.get().search(moduleName + ".k");
        if (filePath == null) {
            return null;
        }

        return loadModuleFromFile(aThis, filePath);
    }

    /**
     * Load a file as a module.
     * This method is a wrap method of Core.load() and Module.loadModuleFromImported()
     * <p>
     * Note: The file must call Module.export() explicitly
     * or we cannot load it as a module.
     *
     * @param aThis    Current namespace, in which we find needed module.
     * @param filePath File name, not file path.
     * @return Module instance
     */
    private static Module loadModuleFromFile(This aThis, String filePath) {
        // If module called Module.export() in source file
        // It must in the imported module list.
        // And we are able to get it.
        String moduleName = parseModuleName(filePath);
        NameSpace ns = Core.load(aThis, filePath, "Module_" + moduleName);
        
        if (ns == null) {
            //return Module.loadModuleFromImported(aThis, parseModuleName(filePath));
            return null;
        }
        
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);
        if (interpreter == null) {
            return null;
        }
        
        Module mod = new Module(moduleName, ns.getThis(interpreter));
        interpreter.getGlobalNameSpace().importModule(mod);
        return mod;
    }

    /**
     * Get a imported module by given name.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param moduleName Module name
     * @return Module instance.
     */
    private static Module loadModuleFromImported(This aThis, String moduleName) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);

        if (interpreter == null) {
            return null;
        }

        NameSpace global = interpreter.getGlobalNameSpace();
        return global.getImportedModule(moduleName);
    }

    /**
     * Get the loaded module by given name.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param moduleName Module name
     * @return Module instance
     * @throws KRuntimeException When module not found.
     */
    public static This load(This aThis, String moduleName) throws KRuntimeException {
        Module mod = loadModuleFromImported(aThis, moduleName);

        // Try to load from ModulePath
        if (mod == null) {
            mod = loadModuleFromModulePath(aThis, moduleName);
        }

        if (mod == null) {
            throw new KRuntimeException("Module " + moduleName + " not found in " + MODULE_PATH.toString());
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
        NameSpace global = interpreter.getGlobalNameSpace();
        global.importModule(mod);
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

    private This moduleThis;
    private String name;

    private Module(String name, This aThis) {
        this.name = name;
        this.moduleThis = aThis;
    }

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

