package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

public class ReturnConditionVisitor extends VoidVisitorAdapter<Void> {
    private final List<Expression> conditions = new ArrayList<>();
    private final ReturnStmt targetReturn;

    public ReturnConditionVisitor(ReturnStmt returnStmt) {
        this.targetReturn = returnStmt;
    }

    @SuppressWarnings("java:S3655")
    @Override
    public void visit(ReturnStmt returnStmt, Void arg) {
        if (!returnStmt.equals(targetReturn)) {
            return;
        }

        Node current = returnStmt;
        boolean hasHandledReturn = false;

        while (!(current instanceof MethodDeclaration) && current != null) {
            if (current instanceof BlockStmt block) {
                Node parent = block.getParentNode().orElse(null);
                if (parent instanceof IfStmt ifStmt) {
                    hasHandledReturn = true;
                    if (ifStmt.getElseStmt().isPresent() &&
                        ifStmt.getElseStmt().get().equals(block)) {
                        conditions.add(BinaryOps.negateCondition(ifStmt.getCondition()));
                    } else {
                        conditions.add(ifStmt.getCondition());
                    }
                }
            }
            current = current.getParentNode().orElse(null);
        }

        if (!hasHandledReturn && current instanceof MethodDeclaration method) {
            method.findFirst(IfStmt.class).ifPresent(ifStmt ->
                conditions.add(BinaryOps.negateCondition(ifStmt.getCondition()))
            );
        }
    }

    public List<Expression> getConditions() {
        return conditions;
    }
}
