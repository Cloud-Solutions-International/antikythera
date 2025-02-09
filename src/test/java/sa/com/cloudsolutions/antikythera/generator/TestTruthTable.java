package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
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
        String condition = "a == null";
        TruthTable generator = new TruthTable(condition);

        List<Map<Expression, Object>> truthTable = generator.getTable();

        assertNotNull(truthTable);
        assertFalse(truthTable.isEmpty());
        assertEquals(2, truthTable.size()); // 2^3 = 8 rows for 3 variables
        assertNull(truthTable.getFirst().get("a"));
    }


    @Test
    void testGenerateTruthTableNumbers() {
        String condition = "a > b && b < c";
        /* Using just 0 and 1 there is exactly one situation where this is always true
         * that is when a = 1, b = 0 and c = 1;
         */
        TruthTable generator = new TruthTable(condition);
        List<Map<Expression, Object>> values = generator.findValuesForCondition(true);
        assertEquals(1, values.size());
        assertEquals(1, values.getFirst().get(new NameExpr("a")));
        assertEquals(0, values.getFirst().get(new NameExpr("b")));
        assertEquals(1, values.getFirst().get(new NameExpr("c")));

    }


    @Test
    void testPrintTruthTable() {
        String condition = "a && b || !c";
        TruthTable generator = new TruthTable(condition);

        generator.printTruthTable();
        assertTrue(outContent.toString().startsWith("Truth Table for condition: a && b || !c\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a && b || c && d", "p && q || r && !s"})
    void testPrintValues(String condition) {
        TruthTable generator = new TruthTable(condition);

        generator.printValues(true);
        assertTrue(outContent.toString().contains("Values to make the condition true for: " + condition));

        generator.printValues(false);
        assertTrue(outContent.toString().contains("Values to make the condition false for: " + condition));
    }

    @Test
    void testInequality() {
        String condition = "a > b && c == d";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(2, v.size());
        Map<Expression, Object> first = v.getFirst();
        assertEquals(1, first.get(new NameExpr("a")));
        assertEquals(0, first.get(new NameExpr("b")));
        assertEquals(1, first.get(new NameExpr("c")));
        assertEquals(1, first.get(new NameExpr("d")));

        v = tt.findValuesForCondition(false);
        assertEquals(14, v.size());
    }

    @Test
    void testSimpleNull() {
        String condition = "a == null";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
        Map<Expression, Object> first = v.getFirst();
        assertNull(first.get(new NameExpr("a")));

        v = tt.findValuesForCondition(false);
        assertEquals(1, v.size());
        first = v.getFirst();
        assertNotNull(first.get(new NameExpr("a")));
    }

    @Test
    void testNotNull() {
        String condition = "a != null && b != null";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
        Map<Expression, Object> first = v.getFirst();
        assertTrue(TruthTable.isTrue(first.get(new NameExpr("a"))));

        v = tt.findValuesForCondition(false);
        assertEquals(3, v.size());
        first = v.getFirst();
        assertNull(first.get(new NameExpr("a")));
    }

    @Test
    void testStringLiteral() {
        String condition = "a.equals(\"b\")";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
    }

    @Test
    void testEquals() {
        String condition = "a.equals(b)";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(2, v.size());
        assertFalse(TruthTable.isTrue(v.getFirst().get(new NameExpr("a"))));
        assertFalse(TruthTable.isTrue(v.getFirst().get(new NameExpr("b"))));
    }

    @Test
    void testEqualsLiteral() {
        String condition = "a.equals(1)";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
        assertTrue(TruthTable.isTrue(v.getFirst().get(new NameExpr("a"))));

    }

    @Test
    void testMethodCall() {
        String condition = "person.getName() != null";
        TruthTable tt = new TruthTable(condition);

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
        Expression first = v.getFirst().keySet().stream().findFirst().orElse(null);
        assertNotNull(first);
        assertTrue(TruthTable.isTrue(v.getFirst().get(first)));
    }
}
