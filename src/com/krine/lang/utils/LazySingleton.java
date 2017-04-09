package com.krine.lang.utils;

public abstract class LazySingleton<T>
{
    private T instance;
    
    public abstract T onCreate();
    
    public T get() {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = onCreate();
                }
            }
        }
        return instance;
    }
}
