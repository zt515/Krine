package krine.extension;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.ast.This;
import com.krine.lang.reflect.Reflect;
import com.krine.lang.reflect.ReflectException;
import krine.core.KRuntimeException;

public final class Extension {
    private KrineBasicInterpreter krineBasicInterperter;
    private String name;
    
    public static void export(This thiz) throws KRuntimeException {
        KrineBasicInterpreter interpreter = null; 
        try {
            // Note: This doesn't provide getters for these field, 
            // And we don't want to neither, so let's use reflection.
            interpreter = (KrineBasicInterpreter) Reflect.getObjectFieldValue(thiz, "declaringKrineBasicInterpreter");
        } catch (UtilEvalException e) {
            throw new KRuntimeException(e.getCause());
        } catch (ReflectException e) {
            throw new KRuntimeException(e.getCause());
        }
        
        if (interpreter == null) {
            throw new KRuntimeException("Cannot get krine instance.");
        }
        
        Extension ext = new Extension(interpreter, thiz.getNameSpace().getName());
        interpreter.getGlobalNameSpace().importExtension(ext);
    }
    
    private Extension(KrineBasicInterpreter krineBasicInterperter, String name) {
        this.krineBasicInterperter = krineBasicInterperter;
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
}

