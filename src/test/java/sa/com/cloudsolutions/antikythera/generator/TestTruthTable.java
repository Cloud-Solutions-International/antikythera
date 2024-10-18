package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestTruthTable {
    final PrintStream standardOut = System.out;
    final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outContent));
        AntikytheraRunTime.reset();
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void testGenerateTruthTable() {
        TruthTable generator = new TruthTable();
        String condition = "a && b || !c";
        Expression expr = StaticJavaParser.parseExpression(condition);

        List<Map<String, Object>> truthTable = generator.generateTruthTable(expr);

        assertNotNull(truthTable);
        assertFalse(truthTable.isEmpty());
        assertEquals(8, truthTable.size()); // 2^3 = 8 rows for 3 variables
    }

    @Test
    void testPrintTruthTable() {
        TruthTable generator = new TruthTable();
        String condition = "a && b || !c";
        Expression expr = StaticJavaParser.parseExpression(condition);

        List<Map<String, Object>> truthTable = generator.generateTruthTable(expr);
        assertTrue((Boolean) truthTable.get(0).get("Result"));
        assertTrue((Boolean) truthTable.get(7).get("Result"));

        generator.printTruthTable(condition, truthTable);
        assertTrue(outContent.toString().startsWith("Truth Table for condition: a && b || !c\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a && b || c && d", "p && q || r && !s"})
    void testPrintTrueValues() {
        TruthTable generator = new TruthTable();
        String condition = "a && b || c && d";
        Expression expr = StaticJavaParser.parseExpression(condition);

        List<Map<String, Object>> truthTable = generator.generateTruthTable(expr);
        generator.printTruthTable(condition, truthTable);

        for(int i =0 ; i < 3 ; i++) {
            Object r = truthTable.get(i).get("Result");
            assertInstanceOf(Boolean.class, r);
            assertFalse( (Boolean)truthTable.get(i).get("Result"));
        }
        assertTrue((Boolean) truthTable.get(3).get("Result"));
        assertTrue(outContent.toString().contains("Truth Table for condition: " + condition));
    }

    @Test
    void testEvaluateCondition() {
        TruthTable generator = new TruthTable();
        String condition = "a && b || !c";
        Expression expr = StaticJavaParser.parseExpression(condition);

        Map<String, Object> truthValues = Map.of("a", true, "b", false, "c", true);
        boolean result = generator.evaluateCondition(expr, truthValues);

        assertFalse(result);
    }
}
