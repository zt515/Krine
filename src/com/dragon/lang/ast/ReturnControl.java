package com.dragon.lang.ast;

/**
 * Represents a Return, Break, or Continue statement
 */
public class ReturnControl implements ParserConstants {
    public int kind;
    public Object value;
    /**
     * The node where we returned... for printing error messages correctly
     */
    public SimpleNode returnPoint;

    public ReturnControl(int kind, Object value, SimpleNode returnPoint) {
        this.kind = kind;
        this.value = value;
        this.returnPoint = returnPoint;
    }
}

