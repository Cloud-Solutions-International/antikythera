package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;

import java.util.List;
import java.util.Map;

public class IfConditionVisitor extends VoidVisitorAdapter<LineOfCode> {

    @Override
    public void visit(IfStmt stmt, LineOfCode parent) {
        // Create LineOfCode for the if statement and set its parent
        LineOfCode ifNode = new LineOfCode(stmt);
        ifNode.setParent(parent);

        Branching.add(ifNode);

        // Handle then branch
        LineOfCode thenNode = new LineOfCode(stmt.getThenStmt());
        thenNode.setParent(ifNode);
        Branching.add(thenNode);

        // Handle else branch
        LineOfCode elseNode = stmt.getElseStmt()
            .map(LineOfCode::new)
            .orElseGet(() -> {
                // Create an explicitly empty BlockStmt for missing else
                BlockStmt emptyBlock = new BlockStmt();
                return new LineOfCode(emptyBlock);
            });
        elseNode.setParent(ifNode);
        Branching.add(elseNode);

        // Use TruthTable to determine preconditions for true and false conditions
        Expression condition = stmt.getCondition();
        try {
            // Create a TruthTable for the condition
            TruthTable truthTable = new TruthTable(condition);
            truthTable.generateTruthTable();

            // Find values that would make the condition evaluate to true
            List<Map<Expression, Object>> trueValues = truthTable.findValuesForCondition(true);
            for (Map<Expression, Object> valueMap : trueValues) {
                for (Map.Entry<Expression, Object> entry : valueMap.entrySet()) {
                    // Create a precondition for each variable assignment that makes the condition true
                    Precondition precondition = new Precondition(entry.getKey());
                    thenNode.addTruePrecondition(precondition);
                }
            }

            // Find values that would make the condition evaluate to false
            List<Map<Expression, Object>> falseValues = truthTable.findValuesForCondition(false);
            for (Map<Expression, Object> valueMap : falseValues) {
                for (Map.Entry<Expression, Object> entry : valueMap.entrySet()) {
                    // Create a precondition for each variable assignment that makes the condition false
                    Precondition precondition = new Precondition(entry.getKey());
                    elseNode.addFalsePrecondition(precondition);
                }
            }
        } catch (Exception e) {
            // If there's an error creating or using the TruthTable, log it and continue
            System.err.println("Error using TruthTable for condition: " + condition);
            e.printStackTrace();
        }

        // Continue visiting nested statements
        stmt.getThenStmt().accept(this, thenNode);
        stmt.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, elseNode));
    }
}
