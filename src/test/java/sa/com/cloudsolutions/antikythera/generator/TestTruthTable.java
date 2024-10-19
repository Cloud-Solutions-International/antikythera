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
        String condition = "a == null";
        TruthTable generator = new TruthTable(condition);

        List<Map<String, Object>> truthTable = generator.getTruthTable();

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
        List<Map<String, Object>> values = generator.findValuesForCondition(true);
        assertEquals(1, values.size());
        assertEquals(1, values.getFirst().get("a"));
        assertEquals(0, values.getFirst().get("b"));
        assertEquals(1, values.getFirst().get("c"));

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
}
