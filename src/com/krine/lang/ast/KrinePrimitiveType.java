package com.krine.lang.ast;

class KrinePrimitiveType extends SimpleNode {
    public Class type;

    KrinePrimitiveType(int id) {
        super(id);
    }

    public Class getType() {
        return type;
    }
}

