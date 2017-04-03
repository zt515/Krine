package com.krine.debugger;

import java.util.Locale;

/**
 * @author kiva
 * @date 2017/3/19
 */
public class BreakPoint {
    private static int ID = 0;

    private int breakPointId = ID++;
    private String file;
    private int line;
    private String code;

    public BreakPoint(String file, int line, String code) {
        this.file = file;
        this.line = line;
        this.code = code;
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public String getCode() {
        return code;
    }

    public int getId() {
        return breakPointId;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "BreakPoint #%d, at %s:%d", getId(), getFile(), getLine());
    }
}
