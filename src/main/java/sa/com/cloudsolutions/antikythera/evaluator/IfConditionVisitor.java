package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class IfConditionVisitor extends VoidVisitorAdapter<LineOfCode> {

    @Override
    public void visit(IfStmt stmt, LineOfCode parent) {
        // Create LineOfCode for the if statement and set its parent
        LineOfCode ifNode = new LineOfCode(stmt);
        ifNode.setParent(parent);

        // Add preconditions for both true and false paths
        addPreconditions(stmt, ifNode);

        Branching.add(ifNode);

        // Handle then branch
        LineOfCode thenNode = new LineOfCode(stmt.getThenStmt());
        thenNode.setParent(ifNode);
        Branching.add(thenNode);

        // Handle else branch
        LineOfCode elseNode = stmt.getElseStmt()
            .map(LineOfCode::new)
            .orElseGet(() -> new LineOfCode(new BlockStmt())); // Empty block for missing else
        elseNode.setParent(ifNode);
        Branching.add(elseNode);

        // Continue visiting nested statements
        stmt.getThenStmt().accept(this, thenNode);
        stmt.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, elseNode));
    }

    /**
     * Adds preconditions to the LineOfCode for both true and false paths.
     * 
     * @param stmt The if statement
     * @param node The LineOfCode representing the if statement
     */
    private void addPreconditions(IfStmt stmt, LineOfCode node) {
        Expression condition = stmt.getCondition();

        // Add the original condition as a precondition for the true path
        Precondition truePrecondition = new Precondition(condition);
        node.addPrecondition(truePrecondition);

        // Create a negated condition for the false path
        Expression negatedCondition = createNegatedCondition(condition);
        Precondition falsePrecondition = new Precondition(negatedCondition);
        node.addPrecondition(falsePrecondition);
    }

    /**
     * Creates a negated version of the given condition.
     * 
     * @param condition The condition to negate
     * @return The negated condition
     */
    private Expression createNegatedCondition(Expression condition) {
        // For simple conditions, we can use UnaryExpr with NOT operator
        if (condition.isBooleanLiteralExpr()) {
            // For boolean literals, just return the opposite value
            boolean value = condition.asBooleanLiteralExpr().getValue();
            return new BooleanLiteralExpr(!value);
        } else {
            // For other expressions, use the NOT operator
            return new UnaryExpr(condition, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
        }
    }
}
