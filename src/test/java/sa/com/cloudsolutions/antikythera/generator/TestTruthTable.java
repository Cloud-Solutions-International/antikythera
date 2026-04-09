package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTruthTable {
    private static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Conditional";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        AntikytheraRunTime.reset();
    }

    @Test
    void mapIsEmptyUsesMapShapedDomains() {
        MethodDeclaration method = compilationUnit()
                .findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals("printMap"))
                .orElseThrow();
        Expression condition = method.findFirst(IfStmt.class).orElseThrow().getCondition();

        TruthTable table = new TruthTable(condition);
        table.addConstraints(List.of(condition));
        table.generateTruthTable();

        List<Map<Expression, Object>> trueRows = table.findValuesForCondition(true);
        List<Map<Expression, Object>> falseRows = table.findValuesForCondition(false);

        Object trueValue = trueRows.stream()
                .map(row -> row.get(new NameExpr("map")))
                .filter(Map.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertInstanceOf(Map.class, trueValue);
        assertFalse(((Map<?, ?>) trueValue).isEmpty());
        assertTrue(falseRows.stream()
                .map(row -> row.get(new NameExpr("map")))
                .anyMatch(value -> value == null || value instanceof Map<?, ?> map && map.isEmpty()));
    }

    @Test
    void objectEqualsUsesObjectCreationDomains() {
        MethodDeclaration method = compilationUnit()
                .findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals("fileCompare"))
                .orElseThrow();
        IfStmt nested = method.findAll(IfStmt.class).get(1);
        MethodCallExpr condition = nested.getCondition().asMethodCallExpr();

        TruthTable table = new TruthTable(condition);
        table.generateTruthTable();

        List<Map<Expression, Object>> falseRows = table.findValuesForCondition(false);
        List<Map<Expression, Object>> trueRows = table.findValuesForCondition(true);

        Object falseValue = falseRows.getFirst().get(new NameExpr("f"));
        Object trueValue = trueRows.getFirst().get(new NameExpr("f"));

        assertInstanceOf(Expression.class, falseValue);
        assertInstanceOf(Expression.class, trueValue);
        assertEquals("new File(\"/tmp\")", ((Expression) trueValue).toString());
        assertEquals("new File(\"/tmp_other\")", ((Expression) falseValue).toString());
    }

    private CompilationUnit compilationUnit() {
        return AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
    }
}
