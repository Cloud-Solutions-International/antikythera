package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * A very basic Runtime for Antikythera.
 *
 * This class will be used to by the Evaluator to mimic a stack and keep track of
 * all the classes that we have compiled.
 */
public class AntikytheraRunTime {
    /**
     * Keeps track of all the classes that we have compiled
     */
    protected static final Map<String, CompilationUnit> resolved = new HashMap<>();
    /**
     * We are not using a stack data structure here, but a Deque. This is because
     * Deque is a double-ended queue, which can be used as a stack. It is more
     * efficient than a Stack in java which is synchronized.
     */
    protected static final Deque<Variable> stack = new LinkedList<>();

    public static CompilationUnit getCompilationUnit(String cls) {
        return resolved.get(cls);
    }

    public static void addClass(String className, CompilationUnit cu) {
        resolved.put(className, cu);
    }

    public static void reset() {
        stack.clear();
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
