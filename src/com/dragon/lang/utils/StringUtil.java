package com.dragon.lang.utils;

import com.dragon.lang.reflect.Reflect;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class StringUtil {

    public static String[] split(String s, String delim) {
        List<String> v = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(s, delim);
        while (st.hasMoreTokens())
            v.add(st.nextToken());
        return v.toArray(new String[0]);
    }

    public static String maxCommonPrefix(String one, String two) {
        int i = 0;
        while (one.regionMatches(0, two, 0, i))
            i++;
        return one.substring(0, i - 1);
    }

    public static String methodString(String name, Class[] types) {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append('(');
        for (int i = 0; i < types.length; i++) {
            Class c = types[i];
            if (i != 0) {
                sb.append(", ");
            }
            sb.append((c == null) ? "null" : c.getName());
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Hack - The real method is in Reflect.java which is not public.
     */
    public static String normalizeClassName(Class type) {
        return Reflect.normalizeClassName(type);
    }
}
