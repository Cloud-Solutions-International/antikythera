package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

        assertEquals(1, setter.getArguments().size());
        assertEquals(expectedArgument, setter.getArgument(0).toString());
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
                        nameKey, "someMethod()")
        );
    }
}
