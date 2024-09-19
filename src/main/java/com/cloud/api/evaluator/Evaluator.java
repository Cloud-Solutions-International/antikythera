package com.cloud.api.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.HashMap;
import java.util.Map;

public class Evaluator {

    public static Map<String, Comparable> contextFactory(CompilationUnit cu) {
        Map<String, Comparable> context = new HashMap<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarationExpr n, Void arg) {
                n.getVariables().forEach(v -> {
                    if (v.getInitializer().isPresent()) {
                        Expression initializer = v.getInitializer().get();
                        if (initializer.isBooleanLiteralExpr()) {
                            context.put(v.getNameAsString(), initializer.asBooleanLiteralExpr().getValue());
                        } else if (initializer.isIntegerLiteralExpr()) {
                            context.put(v.getNameAsString(), Integer.parseInt(initializer.asIntegerLiteralExpr().getValue()));
                        }

                    }
                });
                super.visit(n, arg);
            }
        }, null);
        return context;
    }

    public boolean evaluateCondition(Expression condition, Map<String, Comparable> context) {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();
            Comparable leftValue = evaluateExpression(left, context);
            Comparable rightValue = evaluateExpression(right, context);
            return evaluateBinaryExpression(binaryExpr.getOperator(), leftValue, rightValue);
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            return (boolean) context.getOrDefault(name, false);
        }


        return false;
    }

    private Comparable evaluateExpression(Expression expr, Map<String, Comparable> context) {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return context.get(name);
        } else if (expr.isLiteralExpr()) {
            if (expr.isBooleanLiteralExpr()) {
                return expr.asBooleanLiteralExpr().getValue();
            } else if (expr.isIntegerLiteralExpr()) {
                return Integer.parseInt(expr.asIntegerLiteralExpr().getValue());
            }

        }
        else if(expr.isMethodCallExpr()) {
            MethodCallExpr mc = expr.asMethodCallExpr();
            if(mc.getNameAsString().startsWith("get")) {
                return context.get(mc.getNameAsString().substring(3).toLowerCase());
            }
        }

        return null;
    }

    private boolean evaluateBinaryExpression(BinaryExpr.Operator operator, Comparable leftValue, Comparable rightValue) {
        switch (operator) {
            case EQUALS:
                if(leftValue == null && rightValue == null) return true;
                return leftValue.equals(rightValue);
            case NOT_EQUALS:
                if(leftValue == null && rightValue != null) return true;
                return !leftValue.equals(rightValue);
            case LESS:
                return (int) leftValue < (int) rightValue;
            case GREATER:
                return (int) leftValue > (int) rightValue;
            case LESS_EQUALS:
                return (int) leftValue <= (int) rightValue;
            case GREATER_EQUALS:
                return (int) leftValue >= (int) rightValue;

            default:
                return false;
        }
    }
}
