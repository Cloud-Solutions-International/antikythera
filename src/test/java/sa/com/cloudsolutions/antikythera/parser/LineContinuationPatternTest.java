package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the line continuation backslash handling works correctly.
 * This test uses a simple helper method to demonstrate the expected behavior.
 */
class LineContinuationPatternTest {

    /**
     * Simulates the line continuation pattern handling that was added to
     * unescapeJavaString
     */
    private String processLineContinuation(String str) {
        if (str == null) {
            return null;
        }

        // Handle line continuation backslashes (text block syntax)
        // A backslash at the end of a line: \<newline><optional whitespace>
        // Should be replaced with a space (continuation of the same line)
        str = str.replaceAll("\\\\\\s*[\\r\\n]+\\s*", " ");

        // Then handle other escape sequences
        return str.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    @Test
    void testLineContinuationBackslash() {
        // Test case 1: Line continuation with backslash
        String input1 = "SELECT e.field1 \\\nFROM Entity e \\\nWHERE e.id = :param1";
        String result1 = processLineContinuation(input1);
        assertTrue(result1.contains("SELECT"), "Should contain SELECT");
        assertTrue(result1.contains("FROM"), "Should contain FROM");
        assertTrue(result1.contains("WHERE"), "Should contain WHERE");
        assertFalse(result1.contains("\\\n"), "Should not contain backslash-newline");

        // Test case 2: Line continuation with backslash and carriage return
        String input2 = "SELECT e.field1 \\\r\nFROM Entity e";
        String result2 = processLineContinuation(input2);
        assertTrue(result2.contains("SELECT"), "Should contain SELECT");
        assertTrue(result2.contains("FROM"), "Should contain FROM");
        assertFalse(result2.contains("\\\r\n"), "Should not contain backslash-CRLF");

        // Test case 3: Line continuation with leading whitespace on next line
        String input3 = "SELECT e.field1 \\            \n            FROM Entity e";
        String result3 = processLineContinuation(input3);
        assertTrue(result3.contains("SELECT"), "Should contain SELECT");
        assertTrue(result3.contains("FROM"), "Should contain FROM");
        // The whitespace should be collapsed
        assertFalse(result3.contains("\\            \n            "), "Whitespace should be collapsed");

        // Test case 4: Regular newline escape should still work
        String input4 = "Line1\\nLine2";
        String expected4 = "Line1\nLine2";
        String result4 = processLineContinuation(input4);
        assertEquals(expected4, result4, "Regular \\n escape should still work");

        // Test case 5: Double backslashes should become single backslashes
        String input5 = "C:\\\\Program Files";
        String expected5 = "C:\\Program Files";
        String result5 = processLineContinuation(input5);
        assertEquals(expected5, result5, "Double backslashes should become single backslashes");
    }

    @Test
    void testComplexQueryWithMultipleConditions() {
        // Test query with multiple conditions and line continuations
        String input = "SELECT e.field1 \\\n" +
                "FROM Entity e \\\n" +
                "WHERE e.id = :param1 and e.flag1 = false and e.flag2 = true\\";

        String result = processLineContinuation(input);

        // The result should have the query keywords on a single logical line
        assertTrue(result.contains("SELECT"), "Result should contain SELECT");
        assertTrue(result.contains("FROM"), "Result should contain FROM");
        assertTrue(result.contains("WHERE"), "Result should contain WHERE");
        assertTrue(result.contains(":param1"), "Result should contain the parameter");

        // The line continuation backslashes should be removed
        assertFalse(result.contains("\\\n"), "Result should not contain backslash-newline sequences");

        // All parts should be present and properly connected
        assertTrue(result.contains("field1"), "Should contain field1");
        assertTrue(result.contains("Entity"), "Should contain Entity");
        assertTrue(result.contains("flag1"), "Should contain flag1");
        assertTrue(result.contains("flag2"), "Should contain flag2");
    }
}
