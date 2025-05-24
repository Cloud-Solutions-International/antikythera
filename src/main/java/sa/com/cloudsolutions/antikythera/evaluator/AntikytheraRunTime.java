package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
 * <p>A basic Runtime for Antikythera.</p>
 *
 * This class will be used by the Evaluator to mimic a stack and keep track of
 * all the classes that we have compiled.
 */
public class AntikytheraRunTime {
    /**
     * Keeps track of all the compilation units that we have compiled
     */
    private static final Map<String, CompilationUnit> resolved = new HashMap<>();

    private static final Map<String, TypeWrapper> resolvedTypes = new HashMap<>();
    /**
     * <p>We are not using a stack data structure here, but a Deque. This is because Deque is a
     * double-ended queue, which can be used as a stack. It is more efficient than a Stack ADT.
     * Because in java the stack is synchronized.</p>
     *
     * While it's normal practice to also place the return value of a method call into the
     * stack, we are not doing so in here.
     */
    protected static final Deque<Variable> stack = new LinkedList<>();

    /**
     * Stores the interfaces and their implementations.
     */
    protected static final Map<String, Set<String>> interfaces = new HashMap<>();

    /**
     * Stores parent classes as keys and child classes as values.
     */
    protected static final Map<String, Set<String>> extensions = new HashMap<>();


    /**
     * Stores the fields that have been autowired.
     * While there should not be cyclic dependencies, the reality is that they do exist in the wild.
     * Additionally, due to the way that transactions work in spring boot, you often find classes
     * auto wiring themselves.
     * What this means to us is that setting up the fields will often lead to infinite recursions
     * and stack overflows. To avoid that, lets keep all Autowired instances cached.
     */
    protected static final Map<String, Variable> autowired = new HashMap<>();

    /**
     * Keeps track of static variables.
     * The fully qualified class name is the primary key. The values will be a map, where a field
     * name will be the key and the variable will hold the value of the static field.
     */
    protected static final Map<String, Map<String,Variable>> statics = new HashMap<>();

    private AntikytheraRunTime() {}

    public static CompilationUnit getCompilationUnit(String cls) {
        return resolved.get(cls);
    }

    public static void addType(String className, TypeWrapper typeWrapper) {
        resolvedTypes.put(className, typeWrapper);
    }

    public static void addCompilationUnit(String className, CompilationUnit cu) {
        resolved.put(className, cu);
    }

    public static boolean isServiceClass(String className) {
        TypeWrapper typeWrapper = resolvedTypes.get(className);
        return typeWrapper != null && typeWrapper.isService();
    }

    public static boolean isControllerClass(String className) {
        TypeWrapper typeWrapper = resolvedTypes.get(className);
        return typeWrapper != null && typeWrapper.isController();
    }

    public static boolean isComponentClass(String className) {
        TypeWrapper typeWrapper = resolvedTypes.get(className);
        return typeWrapper != null && typeWrapper.isComponent();
    }

    public static void reset() {
        stack.clear();
    }

    public static void resetAutowires() {
        autowired.clear();
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

    public static boolean isInterface(String name) {
        TypeWrapper typeWrapper = resolvedTypes.get(name);
        return typeWrapper != null && typeWrapper.isInterface();
    }

    @SuppressWarnings("java:S1452")
    public static Optional<TypeDeclaration<?>> getTypeDeclaration(String className) {
        TypeWrapper type = resolvedTypes.get(className);
        return Optional.ofNullable(type).map(TypeWrapper::getType);
    }

    public static void resetAll() {
        stack.clear();
        resolved.clear();
        interfaces.clear();
        extensions.clear();
    }

    public static void addSubClass(String parent, String child) {
        Set<String> s = extensions.computeIfAbsent(parent, k -> new HashSet<>());
        s.add(child);
    }

    public static Set<String> findSubClasses(String parent) {
        return extensions.getOrDefault(parent, new HashSet<>());
    }

    public static void addImplementation(String iface, String impl) {
        Set<String> s = interfaces.computeIfAbsent(iface, k -> new HashSet<>());
        s.add(impl);
    }

    public static Set<String> findImplementations(String iface) {
        return interfaces.getOrDefault(iface, new HashSet<>());
    }

    public static void autoWire(String className, Variable variable) {
        autowired.put(className, variable);
    }

    public static Variable getAutoWire(String className) {
        return autowired.get(className);
    }

    public static Variable getStaticVariable(String fqn, String field) {
        return statics.getOrDefault(fqn, new TreeMap<>()).get(field);
    }

    public static void setStaticVariable(String fqn, String field, Variable variable)
    {
        Map<String, Variable> map = statics.computeIfAbsent(fqn, k -> new TreeMap<>());
        map.put(field, variable);
    }
}
