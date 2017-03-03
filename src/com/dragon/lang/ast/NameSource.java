package com.dragon.lang.ast;

/**
 * This interface supports name completion, which is used primarily for
 * command line tools, etc.  It provides a flat source of "names" in a
 * space.  For example all of the classes in the classpath or all of the
 * variables in a namespace (or all of those).
 * <p>
 * NameSource is the lightest weight mechanism for sources which wish to
 * support name completion.  In the future it might be better for NameSpace
 * to implement NameCompletion directly in a more native and efficient
 * fasion.  However in general name competion is used for human interaction
 * and therefore does not require high performance.
 * <p>
 *
 * @see dragon.util.NameCompletion
 * @see dragon.util.NameCompletionTable
 */
public interface NameSource {
    String[] getAllNames();

    void addNameSourceListener(Listener listener);

    interface Listener {
        void nameSourceChanged(NameSource src);
        /**
         Provide feedback on the progress of mapping a namespace
         @param msg is an update about what's happening
         @perc is an integer in the range 0-100 indicating percentage done
         public void nameSourceMapping(
         NameSource src, String msg, int perc );
         */
    }
}
