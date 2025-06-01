package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.NumericComparator;
import sa.com.cloudsolutions.antikythera.evaluator.ScopeChain;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <p>Generate (and print) truth tables for given conditionals</p>
 *
 * Comparisons involving Object.equals() are tricky. The range of values to assign to the variable
 * depends on the argument to the `equals` method. Obviously, when the scope is null `null.equals`
 * leads to Null Pointer Exceptions, so workarounds will have to be used.
 *
 * The values assigned may have its domain in Strings, Boolean or any other objects. This
 * implementation will only consider Numeric, Boolean and String expressions.
 */
public class TruthTable {
    public static final NameExpr RESULT = new NameExpr("Result");
    public static final String EQUALS_CALL = "equals";
    public static final String IS_EMPTY = "isEmpty";
    /**
     * The condition that this truth table is for
     */
    private Expression condition;
    /**
     * Collection of variables involved in the condition.
     * the key will be the expression representing the variable and the value will be a Pair
     * representing the lower and upper bounds for the expression
     */
    private final HashMap<Expression, Pair<Object, Object>> variables;

    /**
     * All the sub conditions that make up the condition.
     */
    private final Set<Expression> conditions;

    /**
     * If any additional constraints need to be applied, they will be held here.
     */
    private final HashMap<Expression, List<Expression>> constraints;
    /**
     * The matrix of values for the variables and the result of the condition
     */
    private List<Map<Expression, Object>> table;

    /**
     * Should we consider null values when generating the truth table?
     * This setting is only applicable when the condition itself does not contain any null
     * component.
     */
    boolean allowNullInputs = false;

    public TruthTable() {
        this.variables = new HashMap<>();
        this.conditions = new HashSet<>();
        this.constraints = new HashMap<>();
    }
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
        this();
        this.condition = condition;
    }

    private static boolean isInequality(BinaryExpr binaryExpr) {
        return binaryExpr.getOperator() == BinaryExpr.Operator.LESS
                || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER
                || binaryExpr.getOperator() == BinaryExpr.Operator.LESS_EQUALS
                || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS;
    }

    public void setAllowNullInputs(boolean allowNullInputs) {
        this.allowNullInputs = allowNullInputs;
    }

    /**
     * Main method to test the truth table generation and printing with different conditions.
     *
     * @param args Command line arguments.
     */
    @SuppressWarnings("java:S106")
    public static void main(String[] args) {
        String[] conditions = {
                "!a",
                "a > b && c == d",
                "a > b",
                "a == b",
                "a.equals(b)",
                "a.equals(\"b\")",
                "a != null && b != null",
                "a == null",
                "a == null || b == null",
                "a && b || !c",
                "x || y && !z",
                "a > b && b < c",
                "a > b && b > c"
        };

        for (String condition : conditions) {
            TruthTable generator = new TruthTable(condition);
            generator.generateTruthTable();
            generator.printTruthTable();
            generator.printValues(true);
            generator.printValues(false);
            System.out.println("\n");
        }
    }

    /**
     * Generates a truth table for the given condition.
     */
    public void generateTruthTable() {
        this.condition.accept(new ConditionCollector(), conditions);

        // If the condition contains actual null literals (not just comparisons with null),
        // we always allow null inputs regardless of the allowNullInputs setting
        boolean oldState = this.allowNullInputs;

        if (containsNullLiteral(this.condition)) {
            this.allowNullInputs = true;
        }

        this.condition.accept(new VariableCollector(), variables);
        adjustDomain();

        // Restore the original setting after domain adjustment
        this.allowNullInputs = oldState;

        Expression[] variableList = variables.keySet().toArray(new Expression[0]);
        table = new ArrayList<>();
        generateCombinations(variableList);
    }

    /**
     * Checks if the given expression contains any null literals.
     * WHen the expression contains null, we would disregard the allowNullInputs settings when
     * determining the domain.
     * 
     * @param expression The expression to check
     * @return true if the expression contains any null literals that should cause
     *        allowNullInputs to be disregarded, false otherwise
     */
    private boolean containsNullLiteral(Expression expression) {
        if (expression == null) {
            return false;
        }

        if (expression.isEnclosedExpr()) {
            return containsNullLiteral(expression.asEnclosedExpr().getInner());
        }

        if (expression.isBinaryExpr()) {
            BinaryExpr binaryExpr = expression.asBinaryExpr();

            return containsNullLiteral(binaryExpr.getLeft()) || containsNullLiteral(binaryExpr.getRight());
        } 
        // For standalone null literals, consider them as null components
        else if (expression.isNullLiteralExpr()) {
            return true;
        } else if (expression.isMethodCallExpr()) {
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();

            for (Expression arg : methodCallExpr.getArguments()) {
                if (containsNullLiteral(arg)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Creates and fills the truth table.
     * @param variableList all the variables in the conditional.
     */
    private void generateCombinations(Expression[] variableList) {
        Map<Expression, Interval> numericRanges = collectNumericRanges(variableList);
        int totalCombinations = calculateTotalCombinations(variableList, numericRanges);
        table = new ArrayList<>();

        for (int i = 0; i < totalCombinations; i++) {
            Map<Expression, Object> truthValues = generateRowValues(variableList, numericRanges, i);
            // Only add combinations that satisfy all constraints
            if (satisfiesConstraints(truthValues)) {
                Object result = evaluateCondition(condition, truthValues);
                truthValues.put(RESULT, isTrue(result));
                table.add(truthValues);
            }
        }
    }

    private boolean satisfiesConstraints(Map<Expression, Object> truthValues) {
        if (truthValues == null || truthValues.isEmpty()) {
            return false;
        }

        for (Map.Entry<Expression, List<Expression>> constraint : constraints.entrySet()) {
            Expression variable = constraint.getKey();
            for (Expression constraintExpr : constraint.getValue()) {
                if (constraintExpr instanceof BinaryExpr binaryExpr &&
                            !satisfiesConstraintForVariable(variable, binaryExpr, truthValues)) {
                    return false;
                }
                if (constraintExpr instanceof MethodCallExpr mce) {
                    return constraintThroughMethodCall(mce, variable, truthValues);
                }
                if (constraintExpr instanceof UnaryExpr unaryExpr) {
                    Expression e = unaryExpr.getExpression();
                    if (e instanceof MethodCallExpr mce) {
                        boolean b = constraintThroughMethodCall(mce, variable, truthValues);
                        if (unaryExpr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                            return !b;
                        } else {
                            return b;
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean constraintThroughMethodCall(MethodCallExpr mce, Expression variable, Map<Expression, Object> truthValues) {
        Optional<Expression> scope = mce.getScope();
        if (scope.isPresent() && scope.get().equals(variable)) {
            Object value = truthValues.get(variable);
            if (value instanceof Boolean b) {
                return b;
            }
        }
        return false;
    }

    private boolean satisfiesConstraintForVariable(Expression variable, BinaryExpr binaryExpr,
            Map<Expression, Object> truthValues) {
        Object value = truthValues.get(variable);
        if (!(value instanceof Integer intValue)) {
            return true;
        }

        int literalValue = Integer.parseInt(
                binaryExpr.getRight().isIntegerLiteralExpr() ?
                        binaryExpr.getRight().asIntegerLiteralExpr().getValue() :
                        binaryExpr.getLeft().asIntegerLiteralExpr().getValue()
        );

        boolean varOnLeft = binaryExpr.getLeft().toString().equals(variable.toString());
        return switch (binaryExpr.getOperator()) {
            case GREATER -> varOnLeft ? intValue > literalValue : intValue < literalValue;
            case GREATER_EQUALS -> varOnLeft ? intValue >= literalValue : intValue <= literalValue;
            case LESS -> varOnLeft ? intValue < literalValue : intValue > literalValue;
            case LESS_EQUALS -> varOnLeft ? intValue <= literalValue : intValue >= literalValue;
            case EQUALS -> intValue == literalValue;
            case NOT_EQUALS -> intValue != literalValue;
            default -> true;
        };
    }

    /**
     * Depending on the number of variables and their domain, the number of possibilities can change.
     * @param variableList all the variables in the truth table.
     * @param domain the domain of values for integer literals
     * @return the total number of combinations that are available to us.
     */
    private int calculateTotalCombinations(Expression[] variableList,
            Map<Expression, Interval> domain) {
        int totalCombinations = 1;
        for (Expression v : variableList) {
            if (domain.containsKey(v)) {
                totalCombinations *= domain.get(v).width;
            } else {
                totalCombinations *= 2;
            }
        }
        return totalCombinations;
    }
    /**
     * <p>Identifies numeric variables from the list and maps them to their bounds.</p>
     *
     * <p></p>Numeric variables need special treatment and cannot be just treated as [0,1] because
     * certain inequalities can only be filled by considering a wider domain of numbers</p>
     *
     * For example:
     * - For condition "a > b && b > c" with domain [0,2]:
     *   Returns map with {a->2, b->2, c->2}
     * - For condition "a && b" (boolean variables):
     *   Returns empty map
     *
     * @param variableList Array of Expression objects representing variables
     * @return Map of numeric variables to their bounds
     */
    private Map<Expression, Interval> collectNumericRanges(Expression[] variableList) {
        Map<Expression, Interval> numericRanges = new HashMap<>();
        for (Expression v : variableList) {
            Pair<Object, Object> bounds = variables.get(v);
            if (bounds.a instanceof Integer min && bounds.b instanceof Integer max) {
                numericRanges.put(v, new Interval(min, max));
            }
        }
        return numericRanges;
    }

    /**
     * <p>Generates values for each variable in a single row of the truth table.</p>
     *
     * <p>This method converts a numerical combination into specific values for each variable by:
     * 1. For numeric variables (with extended domains):
     *    - Uses modulo arithmetic to select a value within the variable's range
     *    - Example: For range [0,5], combination 7 with product 2 gives (7/2)%6 = 3
     * 2. For non-numeric variables (typically boolean or string):
     *    - Uses binary choice (0 or 1) to select between lower and upper bounds
     *    - Example: For boolean, combination 3 with product 2 gives (3/2)%2 = 1 -> true</p>
     *
     * <p>The 'product' variable maintains the stride length for each variable position, ensuring
     * all possible combinations are covered systematically.</p>
     *
     * @param variableList Array of variables to assign values to
     * @param domain Map of variables to their maximum values (for numeric variables)
     * @param combination Current combination number being processed
     * @return Map of variable expressions to their assigned values
     */
    private Map<Expression, Object> generateRowValues(Expression[] variableList,
                                                      Map<Expression, Interval> domain, int combination) {
        Map<Expression, Object> truthValues = new HashMap<>();
        int product = 1;

        for (Expression v : variableList) {
            if (domain.containsKey(v)) {
                Interval range = domain.get(v);
                int value = range.min + (combination / product) % range.width;
                truthValues.put(v, value);
                product *= range.width;
            } else {
                Pair<Object, Object> bounds = variables.get(v);
                boolean value = ((combination / product) % 2) == 1;
                truthValues.put(v, value ? bounds.b : bounds.a);
                product *= 2;
            }
        }
        return truthValues;
    }

    private void adjustDomain() {
        if (isDefaultDomain()) {
            int maxLiteral = findMaxIntegerLiteral();
            if (maxLiteral > 1) {
                variables.replaceAll((e, v) -> new Pair<>(0, maxLiteral));
            } else {
                for(Map.Entry<Expression, Pair<Object, Object>> entry : variables.entrySet()) {
                    Pair<Object, Object> value = entry.getValue();
                    if (value.a instanceof Number || value.b instanceof Number) {
                        variables.put(entry.getKey(), new Pair<>(0, Math.max(1, variables.size() - 1)));
                    }
                }
            }
        }
        adjustDomainBasedOnConstraints();
    }

    private void adjustDomainBasedOnConstraints() {
        for (Map.Entry<Expression, List<Expression>> constraint : constraints.entrySet()) {
            Expression variable = constraint.getKey();
            for (Expression constraintExpr : constraint.getValue()) {
                if (constraintExpr instanceof BinaryExpr binaryExpr) {
                    adjustDomainForConstraint(variable, binaryExpr);
                }
            }
        }
    }

    private boolean isDefaultDomain() {
        for(Pair<Object, Object> p : variables.values()) {
            if (p.a instanceof Collection<?> || p.b instanceof Collection<?> || p.a instanceof Map<?,?> || p.b instanceof Map<?,?>) {
                continue;
            }
            if (!(p.a instanceof Integer a && p.b instanceof Integer b && a == 0 && b == 1)) {
                return false;
            }
        }
        return true;
    }

    private void adjustDomainForConstraint(Expression variable, BinaryExpr constraint) {
        if (!variables.containsKey(variable)) {
            return;
        }

        Expression value = constraint.getRight().isIntegerLiteralExpr() ?
            constraint.getRight() : constraint.getLeft();
        if (!value.isIntegerLiteralExpr()) {
            return;
        }

        int literalValue = Integer.parseInt(value.asIntegerLiteralExpr().getValue());
        Pair<Object, Object> currentDomain = variables.get(variable);

        if (!(currentDomain.a instanceof Integer && currentDomain.b instanceof Integer)) {
            return;
        }

        Interval newInterval = calculateNewInterval(
            new Interval((Integer) currentDomain.a, (Integer) currentDomain.b),
            literalValue,
            constraint.getOperator(),
            constraint.getLeft().toString().equals(variable.toString())
        );

        variables.put(variable, new Pair<>(newInterval.min, newInterval.max));
    }

    private Interval calculateNewInterval(Interval current, int literalValue,
            BinaryExpr.Operator operator, boolean varOnLeft) {
        return switch (operator) {
            case GREATER -> varOnLeft ?
                new Interval(literalValue + 1, Math.max(current.max, literalValue + 2)) :
                new Interval(Math.min(current.min, literalValue - 2), literalValue - 1);
            case GREATER_EQUALS -> varOnLeft ?
                new Interval(literalValue, Math.max(current.max, literalValue + 1)) :
                new Interval(Math.min(current.min, literalValue - 1), literalValue);
            case LESS -> varOnLeft ?
                new Interval(Math.min(current.min, literalValue - 1), literalValue - 1) :
                new Interval(literalValue + 1, Math.max(current.max, literalValue + 1));
            case LESS_EQUALS -> varOnLeft ?
                new Interval(Math.min(current.min, literalValue), literalValue) :
                new Interval(literalValue, Math.max(current.max, literalValue));
            case EQUALS -> new Interval(literalValue, literalValue);
            default -> current;
        };
    }

    private int findMaxIntegerLiteral() {
        int maxValue = 1;
        for (Expression expr : conditions) {
            if (expr instanceof BinaryExpr binaryExpr) {
                if (isInequality(binaryExpr)) {
                    // Check both sides for integer literals
                    if (binaryExpr.getLeft().isIntegerLiteralExpr()) {
                        int value = Integer.parseInt(binaryExpr.getLeft().asIntegerLiteralExpr().getValue());
                        maxValue = Math.max(maxValue, value + 1);
                    }
                    if (binaryExpr.getRight().isIntegerLiteralExpr()) {
                        int value = Integer.parseInt(binaryExpr.getRight().asIntegerLiteralExpr().getValue());
                        maxValue = Math.max(maxValue, value + 1);
                    }
                }
            } else if (expr instanceof MethodCallExpr methodCall && methodCall.toString().contains(EQUALS_CALL) &&
                    !methodCall.getArguments().isEmpty() && methodCall.getArgument(0).isIntegerLiteralExpr()
            ) {
                // Check equals method arguments for integer literals
                int value = Integer.parseInt(methodCall.getArgument(0).asIntegerLiteralExpr().getValue());
                maxValue = Math.max(maxValue, value + 1);
            }
        }
        return maxValue;
    }

    public static boolean isTrue(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return !n.equals(0);
        }
        if (o instanceof String s) {
            return !s.isEmpty();
        }
        return false;
    }
    /**
     * Prints the truth table for the given condition.
     *
     */
    @SuppressWarnings("java:S106")
    public void printTruthTable() {
        writeTruthTable(System.out);
    }

    private void writeTruthTable(PrintStream out) {
        out.println("Truth Table for condition: " + condition);

        if (!table.isEmpty()) {
            Map<Expression, Object> firstRow = table.get(0);
            final String FORMAT = "%-11s";

            // Sort the keys alphabetically
            List<String> sortedKeys = firstRow.keySet().stream()
                    .map(Expression::toString)
                    .sorted()
                    .toList();

            for (String key : sortedKeys) {
                if (!key.equals(RESULT.toString())) {
                    out.printf(FORMAT, key);
                }
            }
            out.printf(FORMAT, RESULT);
            out.println();

            for (Map<Expression, Object> row : table) {
                for (String key : sortedKeys) {
                    if (!key.equals(RESULT.toString())) {
                        out.printf(FORMAT, row.get(new NameExpr(key)));
                    }
                }
                out.printf(FORMAT, row.get(RESULT));
                out.println();
            }
        } else {
            out.println("No data to display.");
        }
    }

    /**
     * Prints the values that make the condition true.
     */
    @SuppressWarnings("java:S106")
    public void printValues(boolean desiredState) {
        writeValues(desiredState, System.out);
    }

    public void writeValues(boolean desiredState, PrintStream out) {
        String state = desiredState ? "true" : "false";
        out.println("\nValues to make the condition " + state + " for: " + condition);

        List<Map<Expression, Object>> values = findValuesForCondition(desiredState);

        values.stream().findFirst().ifPresentOrElse(
                row -> {
                    // Sort the keys alphabetically
                    List<String> sortedKeys = row.keySet().stream()
                            .map(Expression::toString)
                            .sorted()
                            .toList();

                    for (String key : sortedKeys) {
                        out.printf("%-10s", key + "=" + row.get(new NameExpr(key)));
                    }
                    out.println();
                },
                () -> out.println("No combination of values makes the condition " + state + ".")
        );
    }

    /**
     * Find the values that make the condition true or false.
     * Often there will be more than one combination of values.
     * @param desiredState either true or false
     * @return a list of maps containing the values that make the condition true or false
     */
    public List<Map<Expression, Object>> findValuesForCondition(boolean desiredState) {
        List<Map<Expression, Object>> result = new ArrayList<>();

        for (Map<Expression, Object> row : table) {
            if ((boolean) row.get(RESULT) == desiredState) {
                Map<Expression, Object> copy = new HashMap<>();
                for (Map.Entry<Expression, Object> entry : row.entrySet()) {
                    if (!entry.getKey().equals(RESULT)) {
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
    private Object evaluateCondition(Expression condition, Map<Expression, Object> truthValues) {
        if (condition.isBinaryExpr()) {
            return evaluateBinaryExpression(condition.asBinaryExpr(), truthValues);
        } else if (condition.isUnaryExpr()) {
            var unaryExpr = condition.asUnaryExpr();
            Object value = evaluateCondition(unaryExpr.getExpression(), truthValues);
            if (unaryExpr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                    return !(Boolean) value;
            } else {
                throw new UnsupportedOperationException("Unsupported operator: " + unaryExpr.getOperator());
            }
        } else if (condition.isMethodCallExpr()) {
            return evaluateMethodCall(condition.asMethodCallExpr(), truthValues);
        }

        return evaluateBasicExpression(condition, truthValues);
    }

    private Object evaluateBinaryExpression(BinaryExpr binaryExpr, Map<Expression, Object> truthValues) {
        var leftExpr = binaryExpr.getLeft();
        var rightExpr = binaryExpr.getRight();
        Object left = evaluateCondition(leftExpr, truthValues);
        Object right = evaluateCondition(rightExpr, truthValues);

        return switch (binaryExpr.getOperator()) {
            case AND -> ((Boolean) left) && (Boolean) right;
            case OR -> ((Boolean) left) || (Boolean) right;
            case EQUALS -> (left == null || right == null) ? left == right : left.equals(right);
            case NOT_EQUALS -> (left == null || right == null) ? left != right : !left.equals(right);
            case LESS -> NumericComparator.compare(left, right) < 0;
            case GREATER -> NumericComparator.compare(left, right) > 0;
            case LESS_EQUALS -> NumericComparator.compare(left, right) <= 0;
            case GREATER_EQUALS -> NumericComparator.compare(left, right) >= 0;
            default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
        };
    }

    private Object evaluateBasicExpression(Expression condition, Map<Expression, Object> truthValues) {
        if (condition.isNameExpr()) {
            return truthValues.get(condition);
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isStringLiteralExpr() || condition.isFieldAccessExpr()) {
            return getValue(condition, truthValues);
        } else if (condition.isNullLiteralExpr()) {
            return null;
        } else if (condition.isEnclosedExpr()) {
            return evaluateCondition(condition.asEnclosedExpr().getInner(), truthValues);
        } else if (condition.isIntegerLiteralExpr()) {
            return getValue(condition, truthValues);
        } else if (condition.isDoubleLiteralExpr()) {
            return getValue(condition, truthValues);
        } else if (condition.isLongLiteralExpr()) {
            return getValue(condition, truthValues);
        }

        throw new UnsupportedOperationException("Unsupported expression: " + condition);
    }

    private Object evaluateMethodCall(MethodCallExpr condition, Map<Expression, Object> truthValues) {
        String methodName = condition.getNameAsString();
        Expression scope = condition.getScope().orElse(null);

        if (IS_EMPTY.equals(methodName)) {
            return evaluateIsEmpty(truthValues, scope);
        } else if (EQUALS_CALL.equals(methodName)) {
            return evaluateIsEquals(condition, truthValues, scope);
        }
        return getValue(condition, truthValues);
    }

    private boolean evaluateIsEquals(MethodCallExpr condition, Map<Expression, Object> truthValues, Expression scope) {
        Object scopeValue = truthValues.get(scope);
        Expression argument = condition.getArgument(0);

        if (argument.isLiteralExpr()) {
            if (scopeValue == null) {
                return argument.isNullLiteralExpr();
            }
            if (scopeValue instanceof Boolean b) {
                return b;
            }
            return scopeValue.equals(getValue(argument, truthValues));
        } else {
            Object arg = truthValues.get(argument);
            if (scopeValue == null) {
                return arg == null;
            }
            return scopeValue.equals(arg);
        }
    }

    private static Object evaluateIsEmpty(Map<Expression, Object> truthValues, Expression scope) {
        Object scopeValue = truthValues.get(scope);
        if (scopeValue == null) {
            return true;
        }
        if (scopeValue instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (scopeValue instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /**
     * FInd the appropriate value for the given expression
     * @param expr the conditional expression to find the value for
     * @param truthValues the table containing the values to use
     * @return the value will typically be true/false in some cases it maybe 0/1 and when the
     *      condition has a null in it, we may return null
     */
    private Object getValue(Expression expr, Map<Expression, Object> truthValues) {
        if (expr.isNameExpr()) {
            Object value = truthValues.get(expr);
            if (value instanceof Boolean) {
                return (boolean) value ? 1 : 0;
            } else if (value instanceof Number n) {
                return n.intValue();
            }
        } else if (expr.isLiteralExpr()) {
            Variable v = Evaluator.evaluateLiteral(expr);
            return v.getValue();
        }

        return truthValues.get(expr);
    }

    public List<Map<Expression, Object>> getTable() {
        return table;
    }


    public void addConstraints(List<Expression> collectedConditions) {
        for (Expression cond : collectedConditions) {
            if (cond.isBinaryExpr()) {
                BinaryExpr bin = cond.asBinaryExpr();
                if (bin.getLeft().isNameExpr()) {
                    addConstraint(cond.asBinaryExpr().getLeft().asNameExpr(), cond.asBinaryExpr());
                }
            } else if (cond.isMethodCallExpr()) {
                MethodCallExpr mce = cond.asMethodCallExpr();
                Optional<Expression> expr = mce.getScope();
                if (expr.isPresent()) {
                    addConstraint(expr.get(), mce);
                }
            }
            else if (cond.isUnaryExpr()) {
                UnaryExpr un = cond.asUnaryExpr();
                addConstraint(un, un);
            }
        }
    }

    public void addConstraint(Expression name, Expression constraint) {
        constraints.computeIfAbsent(name, k -> new ArrayList<>()).add(constraint);
    }

    public void setCondition(Expression condition) {
        this.condition = condition;
    }


    private void equalsMethodCall(MethodCallExpr m, HashMap<Expression, Pair<Object, Object>> collector, ScopeChain chain) {
        if (chain.isEmpty()) {
            findDomain(m, collector, m.getArgument(0));
        } else {
            findDomain(chain.getChain().getFirst().getExpression(), collector, m.getArgument(0));
        }
    }

    /**
     * Find the domain for the given name expression
     * @param nameExpression the name expression for which we need to find the domain
     * @param collector the collection into which we will put the eligible expressions
     * @param compareWith the expression that we will compare against.
     */
    private void findDomain(Expression nameExpression, HashMap<Expression,
            Pair<Object, Object>> collector, Expression compareWith) {
        if (compareWith.isNullLiteralExpr()) {
            if (allowNullInputs) {
                collector.put(nameExpression, new Pair<>(null, "T"));
            } else {
                // If null inputs are not allowed, use a non-null domain
                collector.put(nameExpression, new Pair<>(false, true));
            }
        }
        else if (compareWith.isIntegerLiteralExpr()) {
            handleIntegerLiteral(nameExpression, collector, compareWith);
        }
        else if (compareWith.isLongLiteralExpr()) {
            handleLongLiteral(nameExpression, collector, compareWith);
        }
        else if (compareWith.isDoubleLiteralExpr()) {
            handleDoubleLiteral(nameExpression, collector, compareWith);
        }
        else if (compareWith.isStringLiteralExpr()) {
            if (allowNullInputs) {
                collector.put(nameExpression, new Pair<>(null, compareWith.asStringLiteralExpr().getValue()));
            } else {
                // If null inputs are not allowed, use a non-null domain for strings
                collector.put(nameExpression, new Pair<>("", compareWith.asStringLiteralExpr().getValue()));
            }
        }
        else {
            if (isInequalityPresent()) {
                collector.put(nameExpression, new Pair<>(0, 1));
            }
            else {
                collector.put(nameExpression, new Pair<>(true, false));
                collector.put(compareWith, new Pair<>(true, false));
            }
        }
    }

    private void handleIntegerLiteral(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                      Expression compareWith) {
        int literalValue = Integer.parseInt(compareWith.asIntegerLiteralExpr().getValue());
        Node parent = n.getParentNode().orElse(null);

        if (parent instanceof BinaryExpr binaryExpr) {
            if (isInequality(binaryExpr)) {
                handleInequalityDomain(n, collector, literalValue, binaryExpr);
            } else {
                collector.put(n, new Pair<>(literalValue, literalValue + 1));
            }
        } else if (parent instanceof MethodCallExpr methodCallExpr && methodCallExpr.getNameAsString().equals(EQUALS_CALL)) {
            handleEqualsMethodDomain(n, collector, literalValue);
        }
    }

    private void handleLongLiteral(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                   Expression compareWith) {
        // Handle the case where the value might have an 'L' suffix
        String valueStr = compareWith.asLongLiteralExpr().getValue();
        if (valueStr.endsWith("L") || valueStr.endsWith("l")) {
            valueStr = valueStr.substring(0, valueStr.length() - 1);
        }
        long literalValue = Long.parseLong(valueStr);
        Node parent = n.getParentNode().orElse(null);

        if (parent instanceof BinaryExpr binaryExpr) {
            if (isInequality(binaryExpr)) {
                handleLongInequalityDomain(n, collector, literalValue, binaryExpr);
            } else {
                collector.put(n, new Pair<>((int)literalValue, (int)literalValue + 1));
            }
        } else if (parent instanceof MethodCallExpr methodCallExpr && methodCallExpr.getNameAsString().equals(EQUALS_CALL)) {
            handleEqualsMethodDomain(n, collector, (int)literalValue);
        }
    }

    private void handleLongInequalityDomain(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                            long literalValue, BinaryExpr binaryExpr) {
        switch (binaryExpr.getOperator()) {
            case LESS -> collector.put(n, new Pair<>((int)literalValue - 1, (int)literalValue));
            case LESS_EQUALS -> collector.put(n, new Pair<>(0, (int)literalValue + 1));
            case GREATER -> collector.put(n, new Pair<>((int)literalValue - 1, (int)literalValue + 1));
            case GREATER_EQUALS -> collector.put(n, new Pair<>((int)literalValue, (int)literalValue + 2));
            default -> collector.put(n, new Pair<>(0, 1)); // fallback
        }
    }

    private void handleDoubleLiteral(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                     Expression compareWith) {
        double literalValue = Double.parseDouble(compareWith.asDoubleLiteralExpr().getValue());
        Node parent = n.getParentNode().orElse(null);

        if (parent instanceof BinaryExpr binaryExpr) {
            if (isInequality(binaryExpr)) {
                handleDoubleInequalityDomain(n, collector, literalValue, binaryExpr);
            } else {
                collector.put(n, new Pair<>(literalValue, literalValue + 1));
            }
        } else if (parent instanceof MethodCallExpr methodCallExpr && methodCallExpr.getNameAsString().equals(EQUALS_CALL)) {
            handleEqualsMethodDomain(n, collector, (int)literalValue);
        }
    }

    private void handleDoubleInequalityDomain(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                              double literalValue, BinaryExpr binaryExpr) {
        switch (binaryExpr.getOperator()) {
            case LESS -> collector.put(n, new Pair<>(literalValue - 1, literalValue));
            case LESS_EQUALS -> collector.put(n, new Pair<>(0, literalValue + 0.0001));
            case GREATER -> collector.put(n, new Pair<>(literalValue - 1, literalValue + 0.0001));
            case GREATER_EQUALS -> collector.put(n, new Pair<>(literalValue, literalValue + 0.0001));
            default -> collector.put(n, new Pair<>(0, 1)); // fallback
        }
    }

    private void handleInequalityDomain(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                        int literalValue, BinaryExpr binaryExpr) {
        switch (binaryExpr.getOperator()) {
            case LESS -> collector.put(n, new Pair<>(literalValue -1, literalValue));
            case LESS_EQUALS -> collector.put(n, new Pair<>(0, literalValue + 1));
            case GREATER -> collector.put(n, new Pair<>(literalValue - 1, literalValue + 1));
            case GREATER_EQUALS -> collector.put(n, new Pair<>(literalValue, literalValue + 2));
            default -> collector.put(n, new Pair<>(0, 1)); // fallback
        }
    }

    private void handleEqualsMethodDomain(Expression n, HashMap<Expression, Pair<Object, Object>> collector,
                                          int literalValue) {
        if (collector.containsKey(n)) {
            Pair<Object, Object> existingBounds = collector.get(n);
            if (existingBounds.a instanceof Integer min && existingBounds.b instanceof Integer max) {
                if (literalValue < min) {
                    collector.put(n, new Pair<>(literalValue, max));
                } else if (literalValue > max) {
                    collector.put(n, new Pair<>(min, literalValue));
                }
                // If literalValue is within bounds, no action needed
            }
        } else {
            collector.put(n, new Pair<>(literalValue, literalValue));
        }
    }

    /*
     * Does this condition have an inequality as a sub expression
     */
    private boolean isInequalityPresent() {
        for(Expression expr : conditions) {
            if (expr.isBinaryExpr()) {
                BinaryExpr bin = expr.asBinaryExpr();
                if (bin.getOperator().equals(BinaryExpr.Operator.LESS) ||
                        bin.getOperator().equals(BinaryExpr.Operator.GREATER) ||
                        bin.getOperator().equals(BinaryExpr.Operator.LESS_EQUALS) ||
                        bin.getOperator().equals(BinaryExpr.Operator.GREATER_EQUALS)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collects variable names from the condition expression.
     */
    private class VariableCollector extends VoidVisitorAdapter<HashMap<Expression, Pair<Object, Object>>> {
        /**
         * Processes variable names found in conditional expressions and determines their value domains.
         * For each name expression encountered:
         * - If part of an equals() comparison with null, sets domain to [null, "T"]
         *      Truth table does not have any means to determine what should be the reasonable
         *      default value. That information is only available to evaluators. So the
         *      evaluator or other users of this class should set the correct default value.
         * - If part of a numeric comparison, sets domain to [0, numberOfVariables ]
         * - If part of a string comparison, sets domain to [null, "literal"]
         * - For boolean conditions, sets domain to [true, false]
         * Filters out name expressions that are part of method calls or field access.
         *
         * @param n The name expression to analyze
         * @param collector Map storing variable domains as Pairs of lower/upper bounds
         */
        @Override
        public void visit(NameExpr n, HashMap<Expression, Pair<Object, Object>> collector) {
            Optional<Node> parentNode = n.getParentNode();
            if (parentNode.isEmpty()) {
                collector.put(n, new Pair<>(true, false));
            }
            else if (parentNode.get() instanceof BinaryExpr b) {
                findDomain(n, collector, b.getLeft().equals(n) ? b.getRight() : b.getLeft());
            }
            else if (parentNode.get() instanceof UnaryExpr) {
                collector.put(n, new Pair<>(false, true));
            }

            super.visit(n, collector);
        }

        @Override
        public void visit(MethodCallExpr m, HashMap<Expression, Pair<Object, Object>> collector) {
            ScopeChain chain = ScopeChain.findScopeChain(m);
            if (m.getNameAsString().equals(IS_EMPTY)) {
                // For isEmpty(), we want to consider both empty and non-empty collections
                List<?> emptyList = new ArrayList<>();
                List<Integer> nonEmptyList = new ArrayList<>();
                nonEmptyList.add(1);
                Pair<Object, Object> domain = new Pair<>(emptyList, nonEmptyList);
                if (chain.isEmpty()) {
                    collector.put(m, domain);
                } else {
                    collector.put(chain.getChain().getFirst().getExpression(), domain);
                }
            } else if (m.getNameAsString().equals(EQUALS_CALL)) {
                equalsMethodCall(m, collector, chain);
            } else {
                Optional<Node> parent = m.getParentNode();
                if (parent.isPresent() && parent.get() instanceof BinaryExpr b) {
                    if (b.getLeft().equals(m)) {
                        findDomain(m, collector, b.getRight());
                    } else {
                        findDomain(m, collector, b.getLeft());
                    }
                } else {
                    collector.put(m, new Pair<>(true, false));
                }
            }
            super.visit(m, collector);
        }


        @Override
        public void visit(FieldAccessExpr f, HashMap<Expression, Pair<Object, Object>> collector) {
            if(isInequalityPresent()) {
                collector.put(f, new Pair<>(0, 1));
            }
            else {
                collector.put(f, new Pair<>(true, false));
            }
            super.visit(f, collector);
        }
    }

    private static class ConditionCollector extends VoidVisitorAdapter<Set<Expression>> {
        @Override
        public void visit(BinaryExpr b, Set<Expression> collector) {
            collector.add(b);
            super.visit(b, collector);
        }

        /**
         * In this scenario a method call expression will always be `Object.equals` call or a method
         * returning boolean
         * @param m the method call expression
         * @param collector for all the conditional expressions encountered.
         *
         */
        @Override
        public void visit(MethodCallExpr m, Set<Expression> collector) {
            if(m.toString().contains(EQUALS_CALL)) {
                collector.add(m);
            }
            super.visit(m, collector);
        }
    }

    private static class Interval {
        final int min;
        final int max;
        final int width;

        Interval(int min, int max) {
            this.min = min;
            this.max = max;
            this.width = max - min + 1;
        }
    }
}
