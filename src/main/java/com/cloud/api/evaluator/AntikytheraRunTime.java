package com.cloud.api.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class AntikytheraRunTime {
    /**
     * Keeps track of all the classes that we have compiled
     */
    protected static final Map<String, CompilationUnit> resolved = new HashMap<>();
    protected static final Stack<Variable> stack = new Stack<>();

    public static final CompilationUnit getCompilationUnit(String cls) {
        return resolved.get(cls);
    }

    public static void addClass(String className, CompilationUnit cu) {
        resolved.put(className, cu);
    }

    public static void reset() {
        resolved.clear();
    }

    public static class Variable {
        private Type type;
        private Object value;

        public Variable(Type type) {
            this.type = type;
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

}
