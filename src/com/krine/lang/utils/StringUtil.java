package com.krine.lang.utils;

import com.krine.lang.reflect.Reflect;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class StringUtil {

    public static String[] split(String s, String delim) {
        List<String> v = new ArrayList<>();
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

    /**
     * Replaces unprintable characters by their escaped (or unicode escaped)
     * equivalents in the given string
     */
    public static String addEscapes(String str) {
        StringBuilder stringBuilder = new StringBuilder();
        char ch;
        for (int i = 0; i < str.length(); i++) {
            switch (str.charAt(i)) {
                case 0:
                    continue;
                case '\b':
                    stringBuilder.append("\\b");
                    continue;
                case '\t':
                    stringBuilder.append("\\t");
                    continue;
                case '\n':
                    stringBuilder.append("\\n");
                    continue;
                case '\f':
                    stringBuilder.append("\\f");
                    continue;
                case '\r':
                    stringBuilder.append("\\r");
                    continue;
                case '\"':
                    stringBuilder.append("\\\"");
                    continue;
                case '\'':
                    stringBuilder.append("\\\'");
                    continue;
                case '\\':
                    stringBuilder.append("\\\\");
                    continue;
                default:
                    if ((ch = str.charAt(i)) < 0x20 || ch > 0x7e) {
                        String s = "0000" + Integer.toString(ch, 16);
                        stringBuilder.append("\\u").append(s.substring(s.length() - 4, s.length()));
                    } else {
                        stringBuilder.append(ch);
                    }
            }
        }
        return stringBuilder.toString();
    }
}
