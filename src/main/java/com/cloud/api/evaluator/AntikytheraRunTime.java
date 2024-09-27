package com.cloud.api.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

public class AntikytheraRunTime {
    /**
     * Keeps track of all the classes that we have compiled
     */
    protected static final Map<String, CompilationUnit> resolved = new HashMap<>();
    protected static final Deque<Variable> stack = new LinkedList<>();

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

    public static void push(Variable variable) {
        stack.push(variable);
    }

    public static Variable pop() {
        return stack.removeLast();
    }

}
