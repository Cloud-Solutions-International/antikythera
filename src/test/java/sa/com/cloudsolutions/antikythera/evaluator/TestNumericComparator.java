package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestNumericComparator {
    @ParameterizedTest
    @MethodSource("provideNumericComparatorTestCases")
    void testNumericComparator(String testName, Object left, Object right, int expectedResult) {
        // When
        int result = NumericComparator.compare(left, right);

        // Then
        assertEquals(expectedResult, result);
    }

    @Test
    void testThrows() {
        assertThrows(ClassCastException.class,  () -> NumericComparator.compare(100, "100"));
        assertThrows(IllegalArgumentException.class,  () -> NumericComparator.compare(Object.class, "100"));
    }

    private static Stream<Arguments> provideNumericComparatorTestCases() {
        return Stream.of(
            // Double comparisons
            Arguments.of("DoubleEqual", 5.0, 5.0, 0),
            Arguments.of("DoubleLessThan", 3.0, 5.0, -1),
            Arguments.of("DoubleGreaterThan", 7.0, 5.0, 1),
            Arguments.of("DoubleWithInteger", 5.0, 5, 0),

            // Float comparisons
            Arguments.of("FloatEqual", 3.5f, 3.5f, 0),
            Arguments.of("FloatLessThan", 2.5f, 4.5f, -1),
            Arguments.of("FloatGreaterThan", 6.5f, 3.5f, 1),
            Arguments.of("FloatWithInteger", 4.0f, 4, 0),

            // Long comparisons
            Arguments.of("LongEqual", 100L, 100L, 0),
            Arguments.of("LongLessThan", 50L, 100L, -1),
            Arguments.of("LongGreaterThan", 200L, 100L, 1),
            Arguments.of("LongWithInteger", 100L, 100, 0),

            // Integer comparisons
            Arguments.of("IntegerEqual", 42, 42, 0),
            Arguments.of("IntegerLessThan", 20, 42, -1),
            Arguments.of("IntegerGreaterThan", 100, 42, 1),

            // String comparisons
            Arguments.of("StringEqual", "apple", "apple", 0),
            Arguments.of("StringLessThan", "apple", "banana", -1),
            Arguments.of("StringGreaterThan", "zebra", "apple", 25),

            // Comparable objects (using String as Comparable)
            Arguments.of("ComparableEqual", "test", "test", 0),
            Arguments.of("ComparableLessThan", "abc", "xyz", -23),
            Arguments.of("ComparableGreaterThan", "xyz", "abc", 23)
        );
    }
}
