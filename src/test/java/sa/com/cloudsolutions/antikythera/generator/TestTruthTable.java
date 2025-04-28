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

        List<Map<Expression, Object>> values = generator.findValuesForCondition(true);
        assertFalse(values.isEmpty());
        assertNull(values.getFirst().get(new NameExpr("a")));
    }

    @Test
    void testNegative() {
        String condition = "a < 0";
        TruthTable generator = new TruthTable(condition);
        generator.generateTruthTable();

        List<Map<Expression, Object>> values = generator.findValuesForCondition(true);
        assertEquals(1, values.size());
        assertEquals(-1, values.getFirst().get(new NameExpr("a")));
    }

    @Test
    void testNegativeMethod() {
        String condition = "person.getId() < 0";
        TruthTable generator = new TruthTable(condition);
        generator.generateTruthTable();

        List<Map<Expression, Object>> values = generator.findValuesForCondition(true);
        assertEquals(1, values.size());

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
        assertTrue(outContent.toString().contains("true       true       false      true "));
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

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
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

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSimpleNull(boolean allowNullInputs) {
        String condition = "a == null";
        TruthTable tt = new TruthTable(condition);
        tt.setAllowNullInputs(allowNullInputs);
        tt.generateTruthTable();

        // Since the condition contains a null literal, allowNullInputs should be disregarded
        // and null inputs should always be allowed
        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);

        // Null inputs should be allowed regardless of allowNullInputs setting
        assertEquals(1, v.size());
        Map<Expression, Object> first = v.getFirst();
        assertNull(first.get(new NameExpr("a")));

        v = tt.findValuesForCondition(false);
        assertEquals(1, v.size());
        first = v.getFirst();
        assertNotNull(first.get(new NameExpr("a")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, true})
    void testNotNull(boolean allowNullInputs) {
        String condition = "a != null && b != null";
        TruthTable tt = new TruthTable(condition);
        tt.setAllowNullInputs(allowNullInputs);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);

        // This condition doesn't contain null literals, so allowNullInputs should be respected
        if (allowNullInputs) {
            // When null inputs are allowed
            assertEquals(1, v.size());
            Map<Expression, Object> first = v.getFirst();
            assertTrue(TruthTable.isTrue(first.get(new NameExpr("a"))));
            assertTrue(TruthTable.isTrue(first.get(new NameExpr("b"))));

            v = tt.findValuesForCondition(false);
            assertEquals(3, v.size());
            first = v.getFirst();
            assertNull(first.get(new NameExpr("a")));
        } else {
            // When null inputs are disallowed
            // All values should be non-null, so the condition should be true for all combinations
            assertFalse(v.isEmpty());
            for (Map<Expression, Object> row : v) {
                assertNotNull(row.get(new NameExpr("a")));
                assertNotNull(row.get(new NameExpr("b")));
            }

            // There should be no false conditions since all values are non-null
            v = tt.findValuesForCondition(false);
            assertEquals(0, v.size());
        }
    }

    @Test
    void testStringLiteral() {
        String condition = "a.equals(\"b\")";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertEquals(1, v.size());

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
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

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
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

    @ParameterizedTest
    @ValueSource(booleans = {true, true})
    void testMethodCall(boolean allowNullInputs) {
        String condition = "person.getName() != null";
        TruthTable tt = new TruthTable(condition);
        tt.setAllowNullInputs(allowNullInputs);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);

        if (allowNullInputs) {
            // When null inputs are allowed
            assertEquals(1, v.size());
            Expression first = v.getFirst().keySet().stream().findFirst().orElse(null);
            assertNotNull(first);
            assertTrue(TruthTable.isTrue(v.getFirst().get(first)));

            v = tt.findValuesForCondition(false);
            assertFalse(v.isEmpty());
        } else {
            // When null inputs are disallowed
            // All method call results should be non-null
            assertFalse(v.isEmpty());

            // Find the method call expression
            Expression methodCall = v.getFirst().keySet().stream()
                .filter(e -> e.isMethodCallExpr())
                .findFirst()
                .orElse(null);

            if (methodCall != null) {
                // Verify that the method call result is not null
                for (Map<Expression, Object> row : v) {
                    assertNotNull(row.get(methodCall));
                }
            }

            // There should be no false conditions since all values are non-null
            v = tt.findValuesForCondition(false);
            assertEquals(0, v.size());
        }
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

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
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

        v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());
    }

    @Test
    void testLongLiteralExpression() {
        // Test with a long literal in a greater than comparison
        String condition = "a > 5L";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        try {
            List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
            assertFalse(v.isEmpty());

            Map<Expression, Object> first = v.getFirst();
            int value = (int) first.get(new NameExpr("a"));
            assertTrue(value > 5);

            v = tt.findValuesForCondition(false);
            assertFalse(v.isEmpty());
            first = v.getFirst();
            value = (int) first.get(new NameExpr("a"));
            assertTrue(value <= 5);
        } catch (Exception e) {
            // If the test fails with the current approach, let's try a different one
            // This is a fallback to ensure we get some coverage of handleLongLiteral
            System.out.println("[DEBUG_LOG] Exception in testLongLiteralExpression: " + e.getMessage());
            fail("Test failed with exception: " + e.getMessage());
        }
    }

    @Test
    void testNullLiteralDisregardsAllowNullInputs() {
        // Test that when a condition contains null literals, allowNullInputs is disregarded
        String condition = "a == null || b == null";
        TruthTable tt = new TruthTable(condition);

        // Set allowNullInputs to false, but it should be disregarded
        tt.setAllowNullInputs(false);
        tt.generateTruthTable();

        // Verify that null values are still allowed in the truth table
        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());

        // At least one row should have a null value for 'a' or 'b'
        boolean foundNullValue = false;
        for (Map<Expression, Object> row : v) {
            if (row.get(new NameExpr("a")) == null || row.get(new NameExpr("b")) == null) {
                foundNullValue = true;
                break;
            }
        }
        assertTrue(foundNullValue, "Should find at least one row with null values");

        // Check that the condition evaluates correctly
        for (Map<Expression, Object> row : v) {
            Object aValue = row.get(new NameExpr("a"));
            Object bValue = row.get(new NameExpr("b"));
            assertTrue(aValue == null || bValue == null, 
                    "Condition 'a == null || b == null' should be true for this row");
        }
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

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
    }

    @Test
    void testDomainAdjustmentWithLiterals1() {
        String condition = "a > 5 || a.equals(7)";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());
        assertEquals(6, v.getFirst().get(new NameExpr("a")));

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
    }

    @Test
    void testDomainAdjustmentWithLiterals2() {
        String condition = "a > 5 && a.equals(7)";
        TruthTable tt = new TruthTable(condition);
        tt.generateTruthTable();

        List<Map<Expression, Object>> v = tt.findValuesForCondition(true);
        assertFalse(v.isEmpty());
        assertEquals(7, v.getFirst().get(new NameExpr("a")));

        v = tt.findValuesForCondition(false);
        assertFalse(v.isEmpty());
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

        tt.addConstraint(new NameExpr("a"),
                new BinaryExpr(
                        new NameExpr("a"),
                        new IntegerLiteralExpr("10"),
                        BinaryExpr.Operator.LESS_EQUALS
                )
        );

        tt.addConstraint(new NameExpr("a"),
                new BinaryExpr(
                        new NameExpr("a"),
                        new IntegerLiteralExpr("5"),
                        BinaryExpr.Operator.GREATER_EQUALS
                )
        );

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
            assertTrue(bValue >= 5 && bValue <= 10, "b should be between 1 and 3");
            assertTrue((int) row.get(new NameExpr("a")) >= bValue, "a should be greater than or equal to b");
            assertTrue(bValue >= (int) row.get(new NameExpr("c")), "b should be greater than or equal to c");
        }
    }
}
