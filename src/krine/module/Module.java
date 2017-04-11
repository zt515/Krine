package krine.module;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.This;
import krine.core.KRuntimeException;
import com.krine.lang.utils.LazySingleton;
import krine.core.Core;
import com.krine.lang.utils.Capabilities;
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
            for (String path : pathFromEnv.split(Capabilities.envSeparator())) {
                mp.addPath(path);
            }
            
            return mp;
        }
    };
    
    private static String parseModuleName(String fileName) {
        fileName = new File(fileName).getName();
        int index = fileName.lastIndexOf(".");
        return index == -1 ? fileName : fileName.substring(0, index);
    }
    
    /**
     * Search ModulePath to find source file and load it as a module.
     * This method is a wrap method of Core.load() and Module.load()
     *
     * Note: The file must call Module.export() explicitly
     * or we cannot load it as a module.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param fileName   File name, not file path.
     * @return Module instance
     * @throws KRuntimeException When somethings goes wrong.
     * @see Core#load(This, String)
     * @see Module#load(This, String)
     * @see Module#loadFile(This, String)
     * @see Module#loadFile(This, File)
     */
    public static This from(This aThis, String fileName) throws KRuntimeException {
        String filePath = MODULE_PATH.get().search(fileName);
        if (filePath == null) {
            throw new KRuntimeException("Module file " + fileName + " not found in " + MODULE_PATH.toString());
        }
        
        return loadFile(aThis, filePath);
    }
    
    /**
     * Load a file as a module.
     * This method is a wrap method of Core.load() and Module.load()
     *
     * Note: The file must call Module.export() explicitly
     * or we cannot load it as a module.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param fileName   File name, not file path.
     * @return Module instance
     * @throws KRuntimeException When somethings goes wrong.
     * @see Core#load(This, String)
     * @see Module#from(This, String)
     * @see Module#load(This, String)
     * @see Module#loadFile(This, File)
     */
    public static This loadFile(This aThis, String filePath) throws KRuntimeException {
        if (Core.load(aThis, filePath)) {
            return Module.load(aThis, parseModuleName(filePath));
        }

        throw new KRuntimeException("Error loading module file " + filePath);
    }
    
    /**
     * Load a file as a module.
     * This method is a wrap method of Core.load() and Module.load()
     *
     * Note: The file must call Module.export() explicitly
     * or we cannot load it as a module.
     *
     * @param aThis      Current namespace, in which we find needed module.
     * @param fileName   File name, not file path.
     * @return Module instance
     * @throws KRuntimeException When somethings goes wrong.
     * @see Core#load(This, String)
     * @see Module#from(This, String)
     * @see Module#load(This, String)
     * @see Module#loadFile(This, String)
     */
    public static This loadFile(This aThis, File file) throws KRuntimeException {
        return Module.loadFile(aThis, file.getAbsolutePath());
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
        KrineBasicInterpreter interpreter = This.getInterpreter(aThis);

        if (interpreter == null) {
            throw new KRuntimeException("Cannot get krine instance.");
        }

        NameSpace global = interpreter.getGlobalNameSpace();
        Module mod = global.getImportedModule(moduleName);

        if (mod == null) {
            throw new KRuntimeException("Module " + moduleName + " not found.");
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

