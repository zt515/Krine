package krine.module;

import krine.core.KRuntimeException;

/**
 * @author kiva
 * @date 2017/4/14
 */
public class ModuleNotFoundException extends KRuntimeException {

    public ModuleNotFoundException(String moduleName, ModulePath modulePath) {
        super("Module " + moduleName + " not found in " + modulePath.toString());
    }
}
