package com.cloud.api.evaluator;

import com.github.javaparser.ast.CompilationUnit;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

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

    public static void push(Variable variable) {
        stack.push(variable);
    }

    public static Variable pop() {
        return stack.removeLast();
    }

    public static boolean isEmptyStack() {
        return stack.isEmpty();
    }

}
