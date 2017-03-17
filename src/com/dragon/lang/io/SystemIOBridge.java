package com.dragon.lang.io;

import java.io.PrintStream;
import java.io.Reader;

/**
 * The capabilities of a minimal console for Dragon.
 * Stream I/O and optimized print for output.
 * <p>
 * A simple console may ignore some of these or map them to trivial
 * implementations.  e.g. print() with color can be mapped to plain text.
 */
public interface SystemIOBridge {
    Reader getIn();

    PrintStream getOut();

    PrintStream getErr();

    void println(Object o);

    void print(Object o);

    void error(Object o);
}

