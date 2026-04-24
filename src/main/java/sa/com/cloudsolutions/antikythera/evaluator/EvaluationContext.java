package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Encapsulates mutable evaluation state that is currently held as static fields
 * across {@link AntikytheraRunTime} and related classes.
 *
 * <h2>Purpose</h2>
 * <p>This class exists as a migration target: callers can progressively switch from
 * the static accessors on {@code AntikytheraRunTime} to an instance of
 * {@code EvaluationContext}. The instance-based API enables:</p>
 * <ul>
 *   <li>Easier testing — each test can create its own isolated context</li>
 *   <li>Future thread-safety — no shared mutable statics</li>
 *   <li>Clearer ownership — the context is explicitly passed or held by the evaluator</li>
 * </ul>
 *
 * <h2>Migration Path</h2>
 * <ol>
 *   <li>New code should accept / store an {@code EvaluationContext} instead of
 *       calling static methods on {@code AntikytheraRunTime}.</li>
 *   <li>The static {@link #getDefault()} singleton delegates to the same global
 *       maps today, so existing code keeps working.</li>
 *   <li>Over time, replace static call-sites with context-aware equivalents.</li>
 * </ol>
 */
public class EvaluationContext {

    private static final EvaluationContext DEFAULT = new EvaluationContext();

    private final Map<String, CompilationUnit> compilationUnits = new HashMap<>();
    private final Map<String, TypeWrapper> resolvedTypes = new HashMap<>();
    private final Deque<Variable> stack = new LinkedList<>();
    private final Map<String, Set<String>> interfaces = new HashMap<>();
    private final Map<String, Set<String>> extensions = new HashMap<>();
    private final Map<String, Variable> autowired = new HashMap<>();
    private final Map<String, Map<String, Variable>> statics = new HashMap<>();

    /**
     * Returns the global default context that mirrors the current
     * {@link AntikytheraRunTime} static state.
     */
    public static EvaluationContext getDefault() {
        return DEFAULT;
    }

    // ── Compilation-unit registry ──

    public CompilationUnit getCompilationUnit(String className) {
        return compilationUnits.get(className);
    }

    public void addCompilationUnit(String className, CompilationUnit cu) {
        compilationUnits.put(className, cu);
    }

    public Map<String, CompilationUnit> getCompilationUnits() {
        return compilationUnits;
    }

    // ── Type registry ──

    public void addType(String className, TypeWrapper typeWrapper) {
        resolvedTypes.put(className, typeWrapper);
    }

    public TypeWrapper getType(String className) {
        return resolvedTypes.get(className);
    }

    public Map<String, TypeWrapper> getResolvedTypes() {
        return resolvedTypes;
    }

    @SuppressWarnings("java:S1452")
    public Optional<TypeDeclaration<?>> getTypeDeclaration(String className) {
        TypeWrapper type = resolvedTypes.get(className);
        return Optional.ofNullable(type).map(TypeWrapper::getType);
    }

    public boolean isServiceClass(String className) {
        TypeWrapper tw = resolvedTypes.get(className);
        return tw != null && tw.isService();
    }

    public boolean isControllerClass(String className) {
        TypeWrapper tw = resolvedTypes.get(className);
        return tw != null && tw.isController();
    }

    public boolean isComponentClass(String className) {
        TypeWrapper tw = resolvedTypes.get(className);
        return tw != null && tw.isComponent();
    }

    public boolean isInterface(String name) {
        TypeWrapper tw = resolvedTypes.get(name);
        return tw != null && tw.isInterface();
    }

    // ── Stack ──

    public void push(Variable variable) {
        stack.push(variable);
    }

    public Variable pop() {
        return stack.pop();
    }

    public void resetStack() {
        stack.clear();
    }

    // ── Inheritance / interface tracking ──

    public void addSubClass(String parent, String child) {
        extensions.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
    }

    public Set<String> findSubClasses(String parent) {
        return extensions.getOrDefault(parent, new HashSet<>());
    }

    public void addImplementation(String iface, String impl) {
        interfaces.computeIfAbsent(iface, k -> new HashSet<>()).add(impl);
    }

    public Set<String> findImplementations(String iface) {
        return interfaces.getOrDefault(iface, new HashSet<>());
    }

    // ── Autowiring ──

    public void autoWire(String className, Variable variable) {
        autowired.put(className, variable);
    }

    public Variable getAutoWire(String className) {
        return autowired.get(className);
    }

    public void resetAutowires() {
        autowired.clear();
    }

    // ── Static variables ──

    public Variable getStaticVariable(String fqn, String field) {
        return statics.getOrDefault(fqn, new TreeMap<>()).get(field);
    }

    public void setStaticVariable(String fqn, String field, Variable variable) {
        statics.computeIfAbsent(fqn, k -> new TreeMap<>()).put(field, variable);
    }

    public void resetStatics() {
        statics.clear();
    }

    // ── Bulk reset ──

    public void resetAll() {
        stack.clear();
        compilationUnits.clear();
        resolvedTypes.clear();
        interfaces.clear();
        extensions.clear();
        autowired.clear();
        statics.clear();
    }
}
