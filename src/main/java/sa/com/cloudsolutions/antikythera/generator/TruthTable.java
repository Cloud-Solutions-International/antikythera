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
    /**
     * The condition that this truth table is for
     */
    private final Expression condition;
    /**
     * The set of variables involved in the condition
     */
    private final Set<String> variables;
    /**
     * The matrix of values for the variables and the result of the condition
     */
    private List<Map<String, Object>> truthTable;

    /**
     * Create a new truth table for the given condition represented as a string
     * @param conditionCode the condition as string
     */
    public TruthTable(String conditionCode) {
        this(StaticJavaParser.parseExpression(conditionCode));

    }

    /**
     * Create a new truth table for the given condition.
     * @param condition Expression
     */
    public TruthTable(Expression condition) {
        this.condition = condition;
        this.variables = new HashSet<>();
        this.condition.accept(new VariableCollector(), variables);
        generateTruthTable();
    }

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
        String[] conditions = {
                "a == null",
                "a == null || b == null",
                "a != null && b != null",
                "a > b && c == d",
                "a && b || !c",
                "x || y && !z",
                "a > b",
                "a > b && b < c",
                "a > b && b > c"
        };

        for (String condition : conditions) {
            TruthTable generator = new TruthTable(condition);
            generator.printTruthTable();
            generator.printValues(true);
            generator.printValues(false);
            System.out.println("\n");
        }
    }

    /**
     * Generates a truth table for the given condition.
     */
    private void generateTruthTable() {
        List<String> variableList = new ArrayList<>(variables);
        int numVariables = variableList.size();
        int numRows = (int) Math.pow(2, numVariables);

        truthTable = new ArrayList<>();

        for (int i = 0; i < numRows; i++) {
            Map<String, Object> truthValues = new HashMap<>();
            for (int j = 0; j < numVariables; j++) {
                boolean value = (i & (1 << j)) != 0;
                if (!value && condition.toString().contains("null")) {
                    truthValues.put(variableList.get(j), null);
                } else {
                    truthValues.put(variableList.get(j), value);
                }
            }
            boolean result = evaluateCondition(condition, truthValues);
            truthValues.put("Result", result);
            truthTable.add(truthValues);
        }
    }

    /**
     * Prints the truth table for the given condition.
     *
     */
    public void printTruthTable() {
        System.out.println("Truth Table for condition: " + condition);

        if (!truthTable.isEmpty()) {
            Map<String, Object> firstRow = truthTable.get(0);
            for (String var : firstRow.keySet()) {
                System.out.printf("%-10s", var);
            }
            System.out.println();

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
     */
    public void printValues(boolean desiredState) {
        String state = desiredState ? "true" : "false";
        System.out.println("\nValues to make the condition " + state + " for: " + condition);

        List<Map<String, Object>> values = findValuesForCondition(desiredState);

        values.stream().findFirst().ifPresentOrElse(
                row -> {
                    row.entrySet().forEach(var ->System.out.printf("%-10s", var));
                    System.out.println();
                },
                () -> System.out.println("No combination of values makes the condition " + state + ".")
        );
    }

    /**
     * Find the values that make the condition true or false.
     * Often there will be more than one combination of values.
     * @param desiredState either true or false
     * @return a list of maps containing the values that make the condition true or false
     */
    public List<Map<String, Object>> findValuesForCondition(boolean desiredState) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : truthTable) {
            if ((boolean) row.get("Result") == desiredState) {
                Map<String, Object> copy = new HashMap<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!entry.getKey().equals("Result")) {
                        copy.put(entry.getKey(), entry.getValue());
                    }
                }
                result.add(copy);
            }
        }

        return result;
    }

    /**
     * Evaluates the given condition with the provided truth values.
     *
     * @param condition   The condition to evaluate.
     * @param truthValues The truth values for the variables.
     * @return The result of the evaluation.
     */
    private Boolean evaluateCondition(Expression condition, Map<String, Object> truthValues) {
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
                    default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
                };
            } else {
                Boolean left = evaluateCondition(leftExpr, truthValues);
                Boolean right = evaluateCondition(rightExpr, truthValues);
                return switch (binaryExpr.getOperator()) {
                    case AND -> left && right;
                    case OR -> left || right;
                    case EQUALS -> (left == null || right == null) ? left == right : left.equals(right);
                    case NOT_EQUALS -> (left == null || right == null) ? left != right : !left.equals(right);
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

    /**
     * FInd the appropriate value for the given expression
     * @param expr the expression to find the value for
     * @param truthValues the table containing the values to use
     * @return the value will typically be true/false in some cases it maybe 0/1 and when the
     *      condition has a null in it, we may return null
     */
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

    public List<Map<String, Object>> getTruthTable() {
        return truthTable;
    }
}
