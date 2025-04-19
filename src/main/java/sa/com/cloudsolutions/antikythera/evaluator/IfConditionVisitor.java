package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

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

        // Continue visiting nested statements
        stmt.getThenStmt().accept(this, thenNode);
        stmt.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, elseNode));
    }
}
