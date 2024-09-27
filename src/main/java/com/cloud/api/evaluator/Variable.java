package com.cloud.api.evaluator;

import com.github.javaparser.ast.type.Type;

public class Variable {
    private Type type;
    private Object value;

    public Variable(Type type) {
        this.type = type;
    }

    public Variable(Object value) {
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
