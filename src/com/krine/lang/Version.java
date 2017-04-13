package com.krine.lang;

@SuppressWarnings("unused")
public final class Version
{
    public static final String KRINE_1_0 = "Krine 1.0 (original Dragon)";
    public static final String KRINE_2_0 = "Krine 2.0 (Asteroid)";
    public static final String KRINE_2_1 = "Krine 2.1 (Asteroid)";
    public static final String KRINE_2_2 = "Krine 2.2 (Blue)";
    
    public static String current() {
        return KRINE_2_2;
    }
}
