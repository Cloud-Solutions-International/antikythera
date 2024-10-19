package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates and print truth tables for given conditionals
 */
public class TruthTable {

    private static boolean isInequality(BinaryExpr binaryExpr) {
        return binaryExpr.getOperator() == BinaryExpr.Operator.LESS || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER ||
                binaryExpr.getOperator() == BinaryExpr.Operator.LESS_EQUALS || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS;
    }

    /**
     * Main method to test the truth table generation and printing with different conditions.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        TruthTable generator = new TruthTable();

        String[] conditions = {
                " a == null",
                "a == null || b == null",
                "a != null && b != null",
                "a > b && c == d",
                "a && b || !c",
                "x || y && !z",
                "a > b",
                " a > b && b < c"

        };

        for (String condition : conditions) {
            IfStmt ifStmt = StaticJavaParser.parseStatement("if (" + condition + ") {}").asIfStmt();
            Expression expr = ifStmt.getCondition();

            List<Map<String, Object>> truthTable = generator.generateTruthTable(expr);
            generator.printTruthTable(condition, truthTable);
            generator.printTrueValues(condition, truthTable);
            System.out.println();
        }
    }

    /**
     * Generates a truth table for the given condition.
     *
     * @param condition The logical condition attached to an if statement (or any other block).
     * @return A list of maps representing the truth table. Each map contains variable values and the result.
     */
    public List<Map<String, Object>> generateTruthTable(Expression condition) {
        /*
         * We use a set here because the same variable may appear multiple times in the
         * expression.
         */
        Set<String> variables = new HashSet<>();
        condition.accept(new VariableCollector(), variables);

        List<String> variableList = new ArrayList<>(variables);
        int numVariables = variableList.size();
        int numRows = (int) Math.pow(2, numVariables);

        List<Map<String, Object>> truthTable = new ArrayList<>();

        for (int i = 0; i < numRows; i++) {
            Map<String, Object> truthValues = new HashMap<>();
            for (int j = 0; j < numVariables; j++) {
                boolean value = (i & (1 << j)) != 0;
                if (!value && condition.toString().contains("null")) {
                    truthValues.put(variableList.get(j), null);
                }
                else {
                    truthValues.put(variableList.get(j), value);
                }

            }
            boolean result = evaluateCondition(condition, truthValues);
            truthValues.put("Result", result);
            truthTable.add(truthValues);
        }

        return truthTable;
    }

    /**
     * Prints the truth table for the given condition.
     *
     * @param conditionCode The logical condition as a string.
     * @param truthTable    The truth table to print.
     */
    public void printTruthTable(String conditionCode, List<Map<String, Object>> truthTable) {
        System.out.println("Truth Table for condition: " + conditionCode);

        if (!truthTable.isEmpty()) {
            // Print header
            Map<String, Object> firstRow = truthTable.get(0);
            for (String var : firstRow.keySet()) {
                System.out.printf("%-10s", var);
            }
            System.out.println();

            // Print rows
            for (Map<String, Object> row : truthTable) {
                for (Object value : row.values()) {
                    System.out.printf("%-10s", value);
                }
                System.out.println();
            }
        } else {
            System.out.println("No data to display.");
        }
    }

    /**
     * Prints the values that make the condition true.
     *
     * @param conditionCode The logical condition as a string.
     * @param truthTable    The truth table to search for true values.
     */

    public void printTrueValues(String conditionCode, List<Map<String, Object>> truthTable) {
        System.out.println("Values to make the condition true for: " + conditionCode);

        for (Map<String, Object> row : truthTable) {
            if ((boolean) row.get("Result")) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!entry.getKey().equals("Result")) {
                        System.out.println(entry.getKey() + " = " + entry.getValue());
                    }
                }
                return;
            }
        }
        System.out.println("No combination of values makes the condition true.");
    }

    /**
     * Evaluates the given condition with the provided truth values.
     *
     * @param condition   The condition to evaluate.
     * @param truthValues The truth values for the variables.
     * @return The result of the evaluation.
     */
    Boolean evaluateCondition(Expression condition, Map<String, Object> truthValues) {
        if (condition.isBinaryExpr()) {
            var binaryExpr = condition.asBinaryExpr();
            var leftExpr = binaryExpr.getLeft();
            var rightExpr = binaryExpr.getRight();

            if (isInequality(binaryExpr)) {
                int left = (int) getValue(leftExpr, truthValues);
                int right = (int) getValue(rightExpr, truthValues);

                truthValues.put(leftExpr.toString(), left);
                truthValues.put(rightExpr.toString(), right);

                return switch (binaryExpr.getOperator()) {
                    case LESS -> left < right;
                    case GREATER -> left > right;
                    case LESS_EQUALS -> left <= right;
                    case GREATER_EQUALS -> left >= right;
                    default ->
                            throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
                };


            } else {
                Boolean left = evaluateCondition(leftExpr, truthValues);
                Boolean right = evaluateCondition(rightExpr, truthValues);
                return switch (binaryExpr.getOperator()) {
                    case AND -> left && right;
                    case OR -> left || right;
                    case EQUALS -> (left == null || right == null ) ? left == right : left.equals(right);
                    case NOT_EQUALS -> (left == null || right == null ) ? left != right : !left.equals(right);
                    default ->
                            throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
                };
            }
        } else if (condition.isUnaryExpr()) {
            var unaryExpr = condition.asUnaryExpr();
            boolean value = evaluateCondition(unaryExpr.getExpression(), truthValues);
            return switch (unaryExpr.getOperator()) {
                case LOGICAL_COMPLEMENT -> !value;
                default -> throw new UnsupportedOperationException("Unsupported operator: " + unaryExpr.getOperator());
            };
        } else if (condition.isNameExpr()) {
            return (Boolean) truthValues.get(condition.asNameExpr().getNameAsString());
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isMethodCallExpr() || condition.isFieldAccessExpr()) {
            return (Boolean) getValue(condition, truthValues);
        } else if (condition.isNullLiteralExpr()) {
            return null;
        }
        throw new UnsupportedOperationException("Unsupported expression: " + condition);
    }

    private Object getValue(Expression expr, Map<String, Object> truthValues) {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            Object value = truthValues.get(name);
            if (value instanceof Boolean) {
                return (boolean) value ? 1 : 0;
            } else if (value instanceof Number n) {
                return n.intValue();
            }
        } else if (expr.isLiteralExpr()) {
            return switch (expr) {
                case IntegerLiteralExpr integerLiteralExpr -> Integer.valueOf(integerLiteralExpr.getValue());
                case DoubleLiteralExpr doubleLiteralExpr -> Double.valueOf(doubleLiteralExpr.getValue());
                case StringLiteralExpr stringLiteralExpr -> stringLiteralExpr.getValue();
                case NullLiteralExpr nullLiteralExpr -> null;
                default -> throw new UnsupportedOperationException("Unsupported literal expression: " + expr);
            };
        } else if (expr.isMethodCallExpr()) {
            var methodCall = expr.asMethodCallExpr();
            return truthValues.get(methodCall.toString());
        } else if (expr.isFieldAccessExpr()) {
            var fieldAccess = expr.asFieldAccessExpr();
            String scope = fieldAccess.getScope().toString();
            return truthValues.get(scope + "." + fieldAccess.getNameAsString());
        }
        throw new UnsupportedOperationException("Unsupported expression: " + expr);
    }

    /**
     * Collects variable names from the condition expression.
     */
    private static class VariableCollector extends VoidVisitorAdapter<Set<String>> {
        @Override
        public void visit(NameExpr n, Set<String> collector) {
            if (n.getParentNode().isEmpty()) {
                collector.add(n.getNameAsString());
            } else {
                Node parent = n.getParentNode().get();
                if (!(parent instanceof MethodCallExpr || parent instanceof FieldAccessExpr)) {
                    collector.add(n.getNameAsString());
                }
            }
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodCallExpr m, Set<String> collector) {
            collector.add(m.toString());
            super.visit(m, collector);
        }

        @Override
        public void visit(FieldAccessExpr f, Set<String> collector) {
            collector.add(f.toString());
            super.visit(f, collector);
        }
    }
}
