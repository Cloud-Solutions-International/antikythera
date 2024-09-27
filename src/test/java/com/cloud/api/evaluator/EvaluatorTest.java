package com.cloud.api.evaluator;

import com.cloud.api.generator.EvaluatorException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {

    private Map<String, Comparable> context;
    CompilationUnit dto = StaticJavaParser.parse(
            getClass().getClassLoader().getResourceAsStream("sources/com/csi/expressions/SimpleDTO.java"));
    CompilationUnit exp = StaticJavaParser.parse(
            getClass().getClassLoader().getResourceAsStream("sources/com/csi/expressions/SimpleDTOExpressions.java"));


}
