package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds static state accumulated during code evaluation that is used by the test generator.
 * This allows the core evaluators (ControlFlowEvaluator, MockingRegistry, Reflect) to
 * record imports and mock setups without directly depending on TestGenerator.
 */
public class GeneratorState {
    private static List<Expression> whenThen = new ArrayList<>();
    private static Set<ImportDeclaration> imports = new HashSet<>();

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
        imports.add(s);
    }

    public static Set<ImportDeclaration> getImports() {
        return imports;
    }

    public static void clearImports() {
        imports.clear();
    }
}
