package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestControlFlowEvaluator {
    @ParameterizedTest
    @MethodSource("provideBinaryExpressionTestCases")
    void testCreateSetterFromGetterForBinaryExpr(String testName, Expression left, Expression right,
                                                 NameExpr key, String expectedArgument) {
        ControlFlowEvaluator evaluator = EvaluatorFactory.create("", ControlFlowEvaluator.class);
        MethodCallExpr setter = new MethodCallExpr("setValue");
        BinaryExpr binaryExpr = new BinaryExpr(left, right, BinaryExpr.Operator.EQUALS);

        evaluator.createSetterFromGetterForBinaryExpr(setter, binaryExpr, key);
        if (testName.equals("WithNameExpression")) {
            assertEquals(0, setter.getArguments().size());
        }
        else {
            assertEquals(1, setter.getArguments().size());
            assertEquals(expectedArgument, setter.getArgument(0).toString());
        }
    }

    private static Stream<Arguments> provideBinaryExpressionTestCases() {
        NameExpr nameKey = new NameExpr("getValue");

        return Stream.of(
            Arguments.of("LeftSideMatches", nameKey, new StringLiteralExpr("testValue"),
                        nameKey, "\"testValue\""),
            Arguments.of("RightSideMatches", new IntegerLiteralExpr("25"), nameKey,
                        nameKey, "25"),
            Arguments.of("WithLiteralExpression", nameKey, new IntegerLiteralExpr("42"),
                        nameKey, "42"),
            Arguments.of("WithMethodCallExpression", nameKey, new MethodCallExpr("someMethod"),
                        nameKey, "someMethod()"),
            Arguments.of("WithNameExpression", nameKey, new NameExpr("someVariable"),
                        nameKey, "someVariable")
        );
    }

    @ParameterizedTest
    @MethodSource("provideMCETestCases")
    void testCreateSetterFromGetterForMCE(String testName, Expression argument, Object entryValue,
                                         String expectedArgument, int expectedArgumentCount) {
        // Given
        ControlFlowEvaluator evaluator = EvaluatorFactory.create("", ControlFlowEvaluator.class);
        MethodCallExpr setter = new MethodCallExpr("setValue");
        MethodCallExpr mce = new MethodCallExpr("equals");
        mce.addArgument(argument);

        NameExpr key = new NameExpr("getValue");
        Map.Entry<Expression, Object> entry = new AbstractMap.SimpleEntry<>(key, entryValue);

        // When
        evaluator.createSetterFromGetterForMCE(entry, setter, mce);

        // Then
        assertEquals(expectedArgumentCount, setter.getArguments().size());
        if (expectedArgumentCount > 0) {
            assertEquals(expectedArgument, setter.getArgument(0).toString());
        }
    }

    private static Stream<Arguments> provideMCETestCases() {
        return Stream.of(
            Arguments.of("WithObjectCreationExpr",
                        new ObjectCreationExpr().setType("String"),
                        true, "new String()", 1),
            Arguments.of("WithLiteralExprAndTrueValue",
                        new StringLiteralExpr("testValue"),
                        true, "\"testValue\"", 1),
            Arguments.of("WithLiteralExprAndFalseValue",
                        new IntegerLiteralExpr("42"),
                        false, "Integer.valueOf(\"0\")", 1),
            Arguments.of("WithNonLiteralNonObjectCreation",
                        new NameExpr("someVariable"),
                        true, "", 0)
        );
    }

    @ParameterizedTest
    @MethodSource("provideParameterAssignmentTestCases")
    void testParameterAssignment(String testName, String variableType, Expression valueExpr,
                               Object expectedValue, String expectedExpressionString) {
        // Given
        ControlFlowEvaluator evaluator = EvaluatorFactory.create("", ControlFlowEvaluator.class);
        AssignExpr assignExpr = new AssignExpr(new NameExpr("testVar"), valueExpr, AssignExpr.Operator.ASSIGN);

        Variable variable = new Variable(expectedValue);
        try {
            variable.setClazz(Class.forName("java.lang." + variableType));
        } catch (ClassNotFoundException e) {
            variable.setClazz(String.class); // fallback
        }

        // When
        evaluator.parameterAssignment(assignExpr, variable);

        // Then
        assertEquals(expectedValue, variable.getValue());
        if (expectedExpressionString != null) {
            assertEquals(expectedExpressionString, assignExpr.getValue().toString());
        }
    }

    private static Stream<Arguments> provideParameterAssignmentTestCases() {
        return Stream.of(
            Arguments.of("IntegerWithT", "Integer", new StringLiteralExpr("T"), 1, "1"),
            Arguments.of("IntegerWithNull", "Integer", new NullLiteralExpr(), null, null),
            Arguments.of("IntegerWithValue", "Integer", new IntegerLiteralExpr("42"), 42, null),
            Arguments.of("LongWithT", "Long", new StringLiteralExpr("T"), 1L, "1L"),
            Arguments.of("LongWithNull", "Long", new NullLiteralExpr(), null, null),
            Arguments.of("BooleanWithTrue", "Boolean", new BooleanLiteralExpr(true), true, null),
            Arguments.of("StringWithValue", "String", new StringLiteralExpr("test"), "test", null),
            Arguments.of("StringWithNull", "String", new NullLiteralExpr(), null, null)
        );
    }
}
