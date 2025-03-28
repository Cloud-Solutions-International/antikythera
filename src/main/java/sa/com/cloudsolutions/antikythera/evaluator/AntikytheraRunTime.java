package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * <p>A basic Runtime for Antikythera.</p>
 *
 * This class will be used to by the Evaluator to mimic a stack and keep track of
 * all the classes that we have compiled.
 */
public class AntikytheraRunTime {
    /**
     * Keeps track of all the classes that we have compiled
     */
    private static final Map<String, ClassInfo> resolved = new HashMap<>();
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

    private static final Set<String> mockedFields = new HashSet<>();

    /**
     * Stores the fields that have been autowired.
     * While there should not be cyclic dependencies the reality is that they do exist in the wild.
     * Additionally due to the way that transactions work in spring boot, you often find classes
     * auto wiring themselves.
     * What this means to us is that setting up the fields will often lead to infinite recursions
     * and stack overflows. To avoid that lets keep all Autowired instances cached.
     */
    protected static final Map<String, Variable> autowired = new HashMap<>();

    protected static final Map<String, Map<String,Variable>> statics = new HashMap<>();

    private AntikytheraRunTime() {}

    public static CompilationUnit getCompilationUnit(String cls) {
        ClassInfo info = resolved.get(cls);
        if (info != null) {
            return info.getCu();
        }
        return null;
    }

    public static void addClass(String className, CompilationUnit cu) {
        ClassInfo classInfo = ClassInfo.factory(className, cu);
        resolved.put(className, classInfo);
    }

    public static boolean isServiceClass(String className) {
        ClassInfo classInfo = resolved.get(className);
        return classInfo != null && classInfo.serviceClass;
    }

    public static boolean isControllerClass(String className) {
        ClassInfo classInfo = resolved.get(className);
        return classInfo != null && classInfo.controllerClass;
    }

    public static boolean isComponentClass(String className) {
        ClassInfo classInfo = resolved.get(className);
        return classInfo != null && classInfo.componentClass;
    }

    public static void reset() {
        stack.clear();
        mockedFields.clear();
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
        ClassInfo classInfo = resolved.get(name);
        return classInfo != null && classInfo.isInterface;
    }

    public static void markAsMocked(String className) {
        mockedFields.add(className);
    }

    public static boolean isMocked(String className) {
        return mockedFields.contains(className);
    }

    static class ClassInfo {
        private String className;
        private CompilationUnit cu;
        private boolean serviceClass;
        private boolean controllerClass;
        private boolean componentClass;
        private boolean isInterface;
        protected ClassInfo() {}

        public static ClassInfo factory(String className, CompilationUnit cu) {
            ClassInfo classInfo = new ClassInfo();
            classInfo.className = className;
            classInfo.cu = cu;

            cu.getPrimaryType();
            for(TypeDeclaration<?> type : cu.getTypes()) {
                if(type.isPublic()) {
                    if(type.isAnnotationPresent("Service")) {
                        classInfo.serviceClass = true;
                    } else if(type.isAnnotationPresent("RestController")
                                || type.isAnnotationPresent("Controller")) {
                        classInfo.controllerClass = true;
                    } else if(type.isAnnotationPresent("Component")) {
                        classInfo.componentClass = true;
                    }

                    if(type.isClassOrInterfaceDeclaration()) {
                        ClassOrInterfaceDeclaration cdecl = type.asClassOrInterfaceDeclaration();
                        classInfo.isInterface = cdecl.isInterface();
                    }
                }
            }
            return classInfo;
        }

        public String getClassName() {
            return className;
        }

        public CompilationUnit getCu() {
            return cu;
        }
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

    public static Map<String, CompilationUnit> getResolvedClasses() {
        // iterate through the resolved map and return the classnames and the compilation units as a map
        Map<String, CompilationUnit> resolvedClasses = new HashMap<>();
        for (Map.Entry<String, ClassInfo> entry : resolved.entrySet()) {
            resolvedClasses.put(entry.getKey(), entry.getValue().cu);
        }
        return resolvedClasses;
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
