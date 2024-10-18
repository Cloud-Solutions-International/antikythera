package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.*;

/**
 * Generates and print truth tables for given conditionals
 */
public class TruthTable {

    /**
     * Generates a truth table for the given condition.
     *
     * @param condition The logical condition attached to an if statement (or any other block).
     * @return A list of maps representing the truth table. Each map contains variable values and the result.
     */

    public List<Map<String, Object>> generateTruthTable(Expression condition) {
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
                truthValues.put(variableList.get(j), value);
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
                System.out.print(var + "\t");
            }
            System.out.println();

            // Print rows
            for (Map<String, Object> row : truthTable) {
                for (Object value : row.values()) {
                    System.out.print(value + "\t");
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
    boolean evaluateCondition(Expression condition, Map<String, Object> truthValues) {
        if (condition.isBinaryExpr()) {
            var binaryExpr = condition.asBinaryExpr();
            var leftExpr = binaryExpr.getLeft();
            var rightExpr = binaryExpr.getRight();

            if (binaryExpr.getOperator() == BinaryExpr.Operator.LESS || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER) {
                int left = getValue(leftExpr, truthValues);
                int right = getValue(rightExpr, truthValues);
                return switch (binaryExpr.getOperator()) {
                    case LESS -> left < right;
                    case GREATER -> left > right;
                    default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
                };
            } else {
                boolean left = evaluateCondition(leftExpr, truthValues);
                boolean right = evaluateCondition(rightExpr, truthValues);
                return switch (binaryExpr.getOperator()) {
                    case AND -> left && right;
                    case OR -> left || right;
                    case EQUALS -> left == right;
                    case NOT_EQUALS -> left != right;
                    default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
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
            return (boolean) truthValues.get(condition.asNameExpr().getNameAsString());
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        }
        throw new UnsupportedOperationException("Unsupported expression: " + condition);
    }

    private int getValue(Expression expr, Map<String, Object> truthValues) {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return (boolean) truthValues.get(name) ? 1 : 0;
        } else if (expr.isIntegerLiteralExpr()) {
            return expr.asIntegerLiteralExpr().asInt();
        }
        throw new UnsupportedOperationException("Unsupported expression: " + expr);
    }
    /**
     * Collects variable names from the condition expression.
     */
    private static class VariableCollector extends VoidVisitorAdapter<Set<String>> {
        @Override
        public void visit(NameExpr n, Set<String> collector) {
            collector.add(n.getNameAsString());
            super.visit(n, collector);
        }
    }

    /**
     * Main method to test the truth table generation and printing with different conditions.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        TruthTable generator = new TruthTable();

        String[] conditions = {
            "a && b || !c",
            "x || y && !z",
            "p && q || r && !s",
            "a > b && c == d"
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
}
