package com.cloud.api.evaluator;

import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.RestControllerParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);

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

    public boolean evaluateCondition(Expression condition, Map<String, Comparable> context, Map<String, Type> variables) throws EvaluatorException {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            if(binaryExpr.getOperator().equals(BinaryExpr.Operator.AND)) {
                return evaluateCondition(left, context, variables) && evaluateCondition(right, context, variables);
            } else if(binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
                return evaluateCondition(left, context, variables) || evaluateCondition(right, context, variables);
            }
            else {
                Comparable leftValue = evaluateExpression(left, context, variables);
                Comparable rightValue = evaluateExpression(right, context, variables);
                return evaluateBinaryExpression(binaryExpr.getOperator(), leftValue, rightValue);
            }
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            return (boolean) context.getOrDefault(name, false);
        }
        else if(condition.isUnaryExpr()) {
            UnaryExpr unaryExpr = condition.asUnaryExpr();
            Expression expr = unaryExpr.getExpression();
            if(expr.isNameExpr() && variables.containsKey(expr.asNameExpr().getNameAsString())) {
                return false;
            }
            logger.warn("Unary expression not supported yet");
        }

        return false;
    }

    private Comparable evaluateExpression(Expression expr, Map<String, Comparable> context, Map<String, Type> variables) throws EvaluatorException {
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
            // todo fix this hack
            String parts = mc.getScope().get().toString().split("\\.")[0];
            if(variables.containsKey(parts)) {
                throw new EvaluatorException("Method call involving variables not supported yet");
            }
            if(mc.getNameAsString().startsWith("get")) {
                return context.get(mc.getNameAsString().substring(3).toLowerCase());
            }
        }

        return null;
    }

    private boolean evaluateBinaryExpression(BinaryExpr.Operator operator, Comparable leftValue, Comparable rightValue) throws EvaluatorException {
        switch (operator) {
            case EQUALS:
                if(leftValue == null && rightValue == null) return true;
                return leftValue.equals(rightValue);
            case NOT_EQUALS:
                if(leftValue == null) {
                    if (rightValue != null) {
                        return false;
                    }
                    return false;
                }

                return !leftValue.equals(rightValue);
            case LESS:
                return (int) leftValue < (int) rightValue;
            case GREATER:
                return (int) leftValue > (int) rightValue;
            case LESS_EQUALS:
                if(leftValue == null) {
                    throw new EvaluatorException("Left value is null - probably because evaluator is not completed yet");
                }
                return (int) leftValue <= (int) rightValue;
            case GREATER_EQUALS:
                return (int) leftValue >= (int) rightValue;

            default:
                return false;
        }
    }
}
