package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

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
    private static final Map<String, ClassInfo> resolved = new HashMap<>();
    /**
     * We are not using a stack data structure here, but a Deque. This is because
     * Deque is a double-ended queue, which can be used as a stack. It is more
     * efficient than a Stack in java which is synchronized.
     */
    protected static final Deque<Variable> stack = new LinkedList<>();

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
        return resolved.get(className).serviceClass;
    }

    public static boolean isControllerClass(String className) {
        return resolved.get(className).controllerClass;
    }

    public static boolean isComponentClass(String className) {
        return resolved.get(className).componentClass;
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

    static class ClassInfo {
        private String className;
        private CompilationUnit cu;
        private boolean serviceClass;
        private boolean controllerClass;
        private boolean componentClass;

        protected ClassInfo() {}

        public static ClassInfo factory(String className, CompilationUnit cu) {
            ClassInfo classInfo = new ClassInfo();
            classInfo.className = className;
            classInfo.cu = cu;

            for(TypeDeclaration<?> type : cu.getTypes()) {
                if(type.isPublic()) {
                    if(type.isAnnotationPresent("Service")) {
                        classInfo.serviceClass = true;
                    } else if(type.isAnnotationPresent("RestController")) {
                        classInfo.controllerClass = true;
                    } else if(type.isAnnotationPresent("Component")) {
                        classInfo.componentClass = true;
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
}
