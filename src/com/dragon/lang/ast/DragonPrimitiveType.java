package com.dragon.lang.ast;

class DragonPrimitiveType extends SimpleNode {
    public Class type;

    DragonPrimitiveType(int id) {
        super(id);
    }

    public Class getType() {
        return type;
    }
}

