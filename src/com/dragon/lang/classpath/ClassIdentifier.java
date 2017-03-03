package com.dragon.lang.classpath;

public class ClassIdentifier {
    Class clazz;

    public ClassIdentifier(Class clazz) {
        this.clazz = clazz;
    }

    // Can't call it getClass()
    public Class getTargetClass() {
        return clazz;
    }

    public String toString() {
        return "Class Identifier: " + clazz.getName();
    }
}

