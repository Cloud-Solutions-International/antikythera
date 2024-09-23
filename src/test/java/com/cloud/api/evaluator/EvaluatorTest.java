package com.cloud.api.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {

    private Map<String, Comparable> context;
    CompilationUnit dto = StaticJavaParser.parse(
            getClass().getClassLoader().getResourceAsStream("sources/com/csi/expressions/SimpleDTO.java"));
    CompilationUnit exp = StaticJavaParser.parse(
            getClass().getClassLoader().getResourceAsStream("sources/com/csi/expressions/SimpleDTOExpressions.java"));

    @BeforeEach
    void setUp() {

        context = Evaluator.contextFactory(dto);
    }
    @Test
    void testEqualsNull()   {
        Evaluator eval = new Evaluator();
        Expression methodCall = new MethodCallExpr(new NameExpr("dto"), "getHospital");
        Expression nullLiteral = new NullLiteralExpr();
        BinaryExpr condition = new BinaryExpr(methodCall, nullLiteral, BinaryExpr.Operator.EQUALS);
        assertTrue(eval.evaluateCondition(condition, context));
    }
}
