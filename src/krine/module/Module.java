package krine.module;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.ast.NameSpace;
import com.krine.lang.ast.This;
import krine.core.KRuntimeException;

import java.lang.reflect.Field;

/**
 * This class provides Krine the module system.
 *
 * @author kiva
 * @date 2017/4/11
 */
@KrineAPI
@SuppressWarnings("unused")
public final class Module {
    private This moduleThis;
    private String name;

    /**
     * Get the interpreter from This class.
     *
     * @param aThis This class object.
     * @return KrineBasicInterpreter the This object has.
     */
    private static KrineBasicInterpreter getInterpreter(This aThis) throws KRuntimeException {
        try {
            // Note: This doesn't provide getters for these field,
            // And we don't want to neither, so let's use reflection.
            Class<?> clazz = aThis.getClass();
            Field field = clazz.getDeclaredField("declaringKrineBasicInterpreter");
            field.setAccessible(true);
            return (KrineBasicInterpreter) field.get(aThis);
        } catch (NoSuchFieldException e) {
            throw new KRuntimeException("Error reflecting interpreter.");
        } catch (IllegalAccessException e) {
            throw new KRuntimeException("Error accessing interpreter.");
        }
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
        KrineBasicInterpreter interpreter = getInterpreter(aThis);

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
        KrineBasicInterpreter interpreter = getInterpreter(aThis);

        if (interpreter == null) {
            throw new KRuntimeException("Cannot get krine instance.");
        }

        Module mod = new Module(moduleName, aThis);
        NameSpace global = interpreter.getGlobalNameSpace();
        global.importModule(mod);
    }

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

