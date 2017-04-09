package krine.core;

import com.krine.api.annotations.KrineAPI;

/**
 * @author kiva
 * @date 2017/4/9
 */
@KrineAPI
@SuppressWarnings("unused")
public class KRuntimeException extends RuntimeException {
    public KRuntimeException() {
    }

    public KRuntimeException(String message) {
        super(message);
    }

    public KRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public KRuntimeException(Throwable cause) {
        super(cause);
    }

    public KRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
