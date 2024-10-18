package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.*;

public class TruthTable {

    public void generateTruthTable(String conditionCode) {
        IfStmt ifStmt = StaticJavaParser.parseStatement("if (" + conditionCode + ") {}").asIfStmt();
        Expression condition = ifStmt.getCondition();

        Set<String> variables = new HashSet<>();
        condition.accept(new VariableCollector(), variables);

        List<String> variableList = new ArrayList<>(variables);
        int numVariables = variableList.size();
        int numRows = (int) Math.pow(2, numVariables);

        System.out.println("Truth Table for condition: " + conditionCode);
        for (String var : variableList) {
            System.out.print(var + "\t");
        }
        System.out.println("Result");

        Map<String, Boolean> trueValues = null;

        for (int i = 0; i < numRows; i++) {
            Map<String, Boolean> truthValues = new HashMap<>();
            for (int j = 0; j < numVariables; j++) {
                boolean value = (i & (1 << j)) != 0;
                truthValues.put(variableList.get(j), value);
                System.out.print(value + "\t");
            }
            boolean result = evaluateCondition(condition, truthValues);
            System.out.println(result);

            if (result && trueValues == null) {
                trueValues = new HashMap<>(truthValues);
            }
        }

        if (trueValues != null) {
            System.out.println("Values to make the condition true:");
            for (Map.Entry<String, Boolean> entry : trueValues.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
        } else {
            System.out.println("No combination of values makes the condition true.");
        }
    }

    private boolean evaluateCondition(Expression condition, Map<String, Boolean> truthValues) {
        if (condition.isBinaryExpr()) {
            var binaryExpr = condition.asBinaryExpr();
            boolean left = evaluateCondition(binaryExpr.getLeft(), truthValues);
            boolean right = evaluateCondition(binaryExpr.getRight(), truthValues);
            return switch (binaryExpr.getOperator()) {
                case AND -> left && right;
                case OR -> left || right;
                case EQUALS -> left == right;
                case NOT_EQUALS -> left != right;
                default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
            };
        } else if (condition.isUnaryExpr()) {
            var unaryExpr = condition.asUnaryExpr();
            boolean value = evaluateCondition(unaryExpr.getExpression(), truthValues);
            return switch (unaryExpr.getOperator()) {
                case LOGICAL_COMPLEMENT -> !value;
                default -> throw new UnsupportedOperationException("Unsupported operator: " + unaryExpr.getOperator());
            };
        } else if (condition.isNameExpr()) {
            return truthValues.get(condition.asNameExpr().getNameAsString());
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        }
        throw new UnsupportedOperationException("Unsupported expression: " + condition);
    }

    private static class VariableCollector extends VoidVisitorAdapter<Set<String>> {
        @Override
        public void visit(NameExpr n, Set<String> collector) {
            collector.add(n.getNameAsString());
            super.visit(n, collector);
        }
    }
    public static void main(String[] args) {
        TruthTable generator = new TruthTable();

        String[] conditions = {
            "a && b || !c",
            "x || y && !z",
            "p && q || r && !s"
        };

        for (String condition : conditions) {
            generator.generateTruthTable(condition);
            System.out.println();
        }
    }

}
