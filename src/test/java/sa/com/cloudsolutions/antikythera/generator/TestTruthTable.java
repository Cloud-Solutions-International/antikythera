package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestTruthTable {
    final PrintStream standardOut = System.out;
    final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        AntikytheraRunTime.reset();
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void testGenerateTruthTable() {
        String condition = "a == null";
        TruthTable generator = new TruthTable(condition);
        generator.generateTruthTable();

        List<Map<Expression, Object>> truthTable = generator.getTable();

        assertNotNull(truthTable);
        assertFalse(truthTable.isEmpty());
        assertEquals(2, truthTable.size()); // 2^3 = 8 rows for 3 variables
        assertNull(truthTable.getFirst().get("a"));
    }

    @SuppressWarnings("java:S125")
    @Test
    void testGenerateTruthTableNumbers() {
        String condition = "a > b && b < c";
        /* Using just 0 and 1 there is exactly one situation where this is always true
         * that is when a = 1, b = 0 and c = 1;
         */
        TruthTable generator = new TruthTable(condition);
        generator.generateTruthTable();

        List<Map<Expression, Object>> values = generator.findValuesForCondition(true);
        assertEquals(5, values.size());
        assertEquals(1, values.getFirst().get(new NameExpr("a")));
        assertEquals(0, values.getFirst().get(new NameExpr("b")));
        assertEquals(1, values.getFirst().get(new NameExpr("c")));

    }

    @Test
    void testPrintTruthTable() {
        String condition = "a && b || !c";
        TruthTable generator = new TruthTable(condition);
        generator.generateTruthTable();

        generator.printTruthTable();
        assertTrue(outContent.toString().startsWith("Truth Table for condition: a && b || !c\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a && b || c && d", "p && q || r && !s"})
    void testPrintValues(String condition) {
        TruthTable generator = new TruthTable(condition);
        generator.generateTruthTable();

        generator.printValues(true);
        assertTrue(outContent.toString().contains("Values to make the condition true for: " + condition));

        generator.printValues(false);
        assertTrue(outContent.toString().contains("Values to make the condition false for: " + condition));
    }

    @Test
    void testInequality() {
        String condition = "a > b && c == d";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(24, v.size());
        Map<Expression, Object> first = v.getFirst();
        assertEquals(1, first.get(new NameExpr("a")));
        assertEquals(0, first.get(new NameExpr("b")));
        assertEquals(0, first.get(new NameExpr("c")));
        assertEquals(0, first.get(new NameExpr("d")));

        v = tt.findValuesForCondition(false);
        assertEquals(232, v.size());
    }

    @Test
    void testChainedInequality() {
        String condition = "a >= b && b >= c";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(10, v.size());

        Map<Expression, Object> first = v.getFirst();
        assertEquals(0, first.get(new NameExpr("a")));
        assertEquals(0, first.get(new NameExpr("b")));
        assertEquals(0, first.get(new NameExpr("c")));
    }

    @Test
    void testChainedInequality2() {
        String condition = "a > b && b > c";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());

        Map<Expression, Object> first = v.getFirst();
        assertEquals(2, first.get(new NameExpr("a")));
        assertEquals(1, first.get(new NameExpr("b")));
        assertEquals(0, first.get(new NameExpr("c")));
    }

    @Test
    void testSimpleNull() {
        String condition = "a == null";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

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
        tt.generateTruthTable();

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
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
    }

    @Test
    void testEquals() {
        String condition = "a.equals(b)";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(2, v.size());
        Map<Expression, Object> first = v.getFirst();
        assertTrue(Boolean.parseBoolean(first.get(new NameExpr("a")).toString()));
        assertTrue(Boolean.parseBoolean(first.get(new NameExpr("b")).toString()));
    }

    @Test
    void testEqualsLiteral() {
        String condition = "a.equals(1)";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
        assertTrue(TruthTable.isTrue(v.getFirst().get(new NameExpr("a"))));

    }

    @Test
    void testMethodCall() {
        String condition = "person.getName() != null";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());
        Expression first = v.getFirst().keySet().stream().findFirst().orElse(null);
        assertNotNull(first);
        assertTrue(TruthTable.isTrue(v.getFirst().get(first)));
    }

    @Test
    void integrationTest() {
        /*
         * The main method already has a lot of useful stuff
         */
        TruthTable.main(new String[0]);
        String output = outContent.toString();
        assertTrue(output.contains("Values to make the condition true for: !a\na=false"));

    }

    @Test
    void testEnclosedExpression() {
        String condition = "(a && b) || c";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());

        // Test when enclosed expression is true
        Map<Expression, Object> first = v.getFirst();
        assertTrue(TruthTable.isTrue(first.get(new NameExpr("a")))
                && TruthTable.isTrue(first.get(new NameExpr("b")))
                || TruthTable.isTrue(first.get(new NameExpr("c"))));
    }

    @Test
    void testIntegerLiteralExpression() {
        String condition = "a > 5";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());

        Map<Expression, Object> first = v.getFirst();
        int value = (int) first.get(new NameExpr("a"));
        assertTrue(value <= 5);
    }

    @Test
    void testFieldAccess() {
        String condition = "person.age > 18";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());

        Expression fieldAccess = v.getFirst().keySet().stream()
                .filter(e -> e.isFieldAccessExpr())
                .findFirst()
                .orElse(null);
        assertNotNull(fieldAccess);
        assertTrue((int)v.getFirst().get(fieldAccess) > 18);
    }

    @Test
    void testDomainAdjustmentWithLiterals() {
        String condition = "a > 5 || a.equals(7)";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());

        // Test values larger than the default [0,1] domain
        Set<Object> values = v.stream()
                .map(m -> m.get(new NameExpr("a")))
                .collect(Collectors.toSet());

        assertTrue(values.contains(6));
        assertTrue(values.contains(7));
    }

    @Test
    void testWithConstraints() {
        String condition = "a > b && b > c";
        TruthTable tt = new TruthTable(condition);

        // Add constraint: a must be less than 5
        tt.addConstraint(new NameExpr("a"),
            new BinaryExpr(
                new NameExpr("a"),
                new IntegerLiteralExpr("5"),
                BinaryExpr.Operator.GREATER
            )
        );

        tt.generateTruthTable();
        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);

        assertFalse(v.isEmpty());
        for (Map<Expression, Object> row : v) {
            int aValue = (int) row.get(new NameExpr("a"));
            assertTrue(aValue > 5, "a should be greater than 5");
            assertTrue(aValue > (int) row.get(new NameExpr("b")), "a should be greater than b");
        }
    }

    @Test
    void testMultipleConstraints() {
        String condition = "a >= b && b >= c";
        TruthTable tt = new TruthTable(condition);

        // Add constraints: 1 <= b <= 3
        tt.addConstraint(new NameExpr("b"),
            new BinaryExpr(
                new NameExpr("b"),
                new IntegerLiteralExpr("5"),
                BinaryExpr.Operator.GREATER_EQUALS
            )
        );
        tt.addConstraint(new NameExpr("b"),
            new BinaryExpr(
                new NameExpr("b"),
                new IntegerLiteralExpr("10"),
                BinaryExpr.Operator.LESS_EQUALS
            )
        );

        tt.generateTruthTable();
        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);

        assertFalse(v.isEmpty());
        for (Map<Expression, Object> row : v) {
            int bValue = (int) row.get(new NameExpr("b"));
            assertTrue(bValue >= 1 && bValue <= 3, "b should be between 1 and 3");
            assertTrue((int) row.get(new NameExpr("a")) >= bValue, "a should be greater than or equal to b");
            assertTrue(bValue >= (int) row.get(new NameExpr("c")), "b should be greater than or equal to c");
        }
    }
}
