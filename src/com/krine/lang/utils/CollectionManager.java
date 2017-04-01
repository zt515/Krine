package com.krine.lang.utils;

import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

/**
 * The default CollectionManager
 * supports iteration over objects of type:
 * Enumeration, Iterator, Iterable, CharSequence, and array.
 */
public final class CollectionManager {
    private static final CollectionManager manager = new CollectionManager();

    public synchronized static CollectionManager getCollectionManager() {
        return manager;
    }


    public boolean isKrineIterable(Object obj) {
        // TODO Be smarter
        try {
            getKrineIterator(obj);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Iterator getKrineIterator(Object obj)
            throws IllegalArgumentException {
        if (obj == null)
            throw new NullPointerException("Cannot iterate over null.");

        if (obj instanceof Enumeration) {
            final Enumeration enumeration = (Enumeration) obj;
            return new Iterator<Object>() {
                public boolean hasNext() {
                    return enumeration.hasMoreElements();
                }

                public Object next() {
                    return enumeration.nextElement();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        if (obj instanceof Iterator)
            return (Iterator) obj;

        if (obj instanceof Iterable)
            return ((Iterable) obj).iterator();

        if (obj.getClass().isArray()) {
            final Object array = obj;
            return new Iterator() {
                private int index = 0;
                private final int length = Array.getLength(array);

                public boolean hasNext() {
                    return index < length;
                }

                public Object next() {
                    return Array.get(array, index++);
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        if (obj instanceof CharSequence)
            return getKrineIterator(
                    obj.toString().toCharArray());

        throw new IllegalArgumentException(
                "Cannot iterate over object of type " + obj.getClass());
    }

    public boolean isMap(Object obj) {
        return obj instanceof Map;
    }

    public Object getFromMap(Object map, Object key) {
        return ((Map) map).get(key);
    }

    public Object putInMap(Object map, Object key, Object value) {
        return ((Map) map).put(key, value);
    }

}
