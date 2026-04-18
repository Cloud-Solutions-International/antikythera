package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds static state accumulated during code evaluation that is used by the test generator.
 * This allows the core evaluators (ControlFlowEvaluator, MockingRegistry, Reflect) to
 * record imports and mock setups without directly depending on TestGenerator.
 */
public class GeneratorState {
    private static List<Expression> whenThen = new ArrayList<>();
    private static Set<ImportDeclaration> imports = new HashSet<>();

    /** {@code scopeVariable|methodName} → fully qualified cast target for methods that return Object. */
    private static final Map<String, String> mockStubReturnHints = new HashMap<>();

    /**
     * When evaluating {@code (T) mock.call()}, holds {@code T} so Mockito stubs use a cast-compatible
     * {@code thenReturn} value instead of {@code new Object()}.
     */
    private static final Deque<String> pendingObjectStubReturnFqns = new ArrayDeque<>();

    private GeneratorState() {}

    public static void addWhenThen(Expression expr) {
        whenThen.add(expr);
    }

    public static void clearWhenThen() {
        whenThen.clear();
    }

    public static List<Expression> getWhenThen() {
        return whenThen;
    }

    public static void addImport(ImportDeclaration s) {
        // Never allow internal antikythera classes or private/anonymous JDK classes ($) into
        // generated test files — they are not on the test classpath and would cause compile errors.
        String name = s.getNameAsString();
        if (name.startsWith("sa.com.cloudsolutions.antikythera.") || name.contains("$")) {
            return;
        }
        imports.add(s);
    }

    public static Set<ImportDeclaration> getImports() {
        return imports;
    }

    public static void clearImports() {
        imports.clear();
    }

    public static void clearMockStubReturnHints() {
        mockStubReturnHints.clear();
    }

    public static void putMockStubReturnHint(String scopeVariableName, String methodName, String returnTypeFqn) {
        if (scopeVariableName == null || methodName == null || returnTypeFqn == null) {
            return;
        }
        mockStubReturnHints.put(scopeVariableName + "|" + methodName, returnTypeFqn);
    }

    public static String getMockStubReturnHint(String scopeVariableName, String methodName) {
        if (scopeVariableName == null || methodName == null) {
            return null;
        }
        return mockStubReturnHints.get(scopeVariableName + "|" + methodName);
    }

    public static void pushPendingObjectStubReturnFqn(String fqn) {
        if (fqn != null) {
            pendingObjectStubReturnFqns.push(fqn);
        }
    }

    public static void popPendingObjectStubReturnFqn() {
        if (!pendingObjectStubReturnFqns.isEmpty()) {
            pendingObjectStubReturnFqns.pop();
        }
    }

    public static String peekPendingObjectStubReturnFqn() {
        return pendingObjectStubReturnFqns.isEmpty() ? null : pendingObjectStubReturnFqns.peek();
    }

    public static void clearPendingObjectStubReturnFqns() {
        pendingObjectStubReturnFqns.clear();
    }
}
