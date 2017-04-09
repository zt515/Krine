package krine.core;

import com.krine.api.annotations.KrineAPI;
import com.krine.lang.utils.LazySingleton;

import java.util.HashMap;
import java.util.Locale;

/**
 * This class provides some profile APIs to Krine.
 * Such as execution timer, profile log, etc.
 *
 * @author kiva
 * @date 2017/4/9
 */
@KrineAPI
@SuppressWarnings("unused")
public final class Profiler {
    private static final LazySingleton<HashMap<String, Long>> TIMER_TAGS = new LazySingleton<HashMap<String, Long>>() {
        @Override
        public HashMap<String, Long> onCreate() {
            return new HashMap<>();
        }
    };

    /**
     * Start a timer for statement profile.
     *
     * @param tag Timer tag
     * @see Profiler#end(String)
     */
    public static void begin(String tag) {
        HashMap<String, Long> tags = TIMER_TAGS.get();
        synchronized (TIMER_TAGS) {
            if (tags.containsKey(tag)) {
                throw new KRuntimeException("Timer " + tag + " already in use.");
            }
            tags.put(tag, Core.getTime());
        }
    }

    /**
     * Finish a timer and print result.
     *
     * @param tag Timer tag
     * @see Profiler#begin(String)
     */
    public static void end(String tag) {
        HashMap<String, Long> tags = TIMER_TAGS.get();
        long start;
        long end = Core.getTime();

        synchronized (TIMER_TAGS) {
            if (!tags.containsKey(tag)) {
                throw new KRuntimeException("Timer " + tag + " does not exist.");
            }

            start = tags.get(tag);
            tags.remove(tag);
        }

        System.err.printf(Locale.getDefault(), "[%s] execution time: %dms\n", tag, end - start);
    }
}
