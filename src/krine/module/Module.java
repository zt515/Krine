package krine.module;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.This;
import com.krine.lang.utils.Capabilities;
import com.krine.lang.utils.LazySingleton;
import java.io.File;
import krine.core.Core;
import krine.core.KRuntimeException;

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
    
    public static void addModuleSearchPath(String path) {
        Module.addModuleSearchPath(new File(path));
    }
    
    public static void addModuleSearchPath(File file) {
        if (file.isDirectory() && file.canRead()) {
            MODULE_PATH.get().addPath(file.getAbsolutePath());
        }
    }
    
    /**
     * Get pure file name without path and suffix.
     * eg. /base/app.k => app
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
     * @param fileName   File name, not file path.
     * @return Module instance
     * @throws KRuntimeException When somethings goes wrong.
     * @see Module#loadModuleFromFile(This, String)
     */
    private static Module loadModuleFromModulePath(This aThis, String moduleName) throws KRuntimeException {
        String filePath = MODULE_PATH.get().search(moduleName + ".k");
        if (filePath == null) {
            return null;
        }
        
        return loadModuleFromFile(aThis, filePath);
    }
    
    /**
     * Load a file as a module.
     * This method is a wrap method of Core.load() and Module.loadModuleFromImported()
     *
     * Note: The file must call Module.export() explicitly
     * or we cannot load it as a module.
     *
     * @param aThis    Current namespace, in which we find needed module.
     * @param fileName File name, not file path.
     * @return Module instance
     * @throws KRuntimeException When somethings goes wrong.
     */
    private static Module loadModuleFromFile(This aThis, String filePath) throws KRuntimeException {
        // If module called Module.export() in source file
        // It must in the imported module list.
        // And we are able to get it.
        if (Core.load(aThis, filePath)) {
            return Module.loadModuleFromImported(aThis, parseModuleName(filePath));
        }

        return null;
    }
    
    /**
     * Get a imported module by given name.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param moduleName Module name
     * @return Module instance.
     * @throw KRuntimeException When module not found in imported module list..
     */
    private static Module loadModuleFromImported(This aThis, String moduleName) {
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);

        if (interpreter == null) {
            throw new KRuntimeException("Cannot get krine instance.");
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
     * @throws KRuntimeException When somethings goes wrong.
     */
    public static This load(This aThis, String moduleName) throws KRuntimeException {
        Module mod = loadModuleFromImported(aThis, moduleName);
        
        // Try load from ModulePath
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
