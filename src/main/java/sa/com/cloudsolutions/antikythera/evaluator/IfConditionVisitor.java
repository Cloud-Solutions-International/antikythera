package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class IfConditionVisitor extends VoidVisitorAdapter<LineOfCode> {

    @Override
    public void visit(IfStmt stmt, LineOfCode parent) {
        LineOfCode lineOfCode = new LineOfCode(stmt);
        lineOfCode.setParent(parent);
        Branching.add(lineOfCode);

        // Visit the "then" branch
        stmt.getThenStmt().accept(this, lineOfCode);

        // Visit the "else" branch if it exists
        stmt.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, lineOfCode));
    }


    /**
     * Given a statement find all the conditions that are required to be met to reach that line
     * @param stmt a statement to search upwards from
     * @return the list of conditions that need to be met to reach this line
     */
    public static List<Expression> collectConditionsUpToMethod(Statement stmt) {
        List<Expression> conditions = new ArrayList<>();
        Node current = stmt;

        while (current != null && !(current instanceof MethodDeclaration)) {
            Optional<Node> parentNode = current.getParentNode();
            if (parentNode.isEmpty()) break;

            Node parent = parentNode.get();
            if (parent instanceof IfStmt ifStmt) {
                Statement thenStmt = ifStmt.getThenStmt();
                Optional<Statement> elseStmt = ifStmt.getElseStmt();

                if (isNodeInStatement(current, thenStmt)) {
                    conditions.add(ifStmt.getCondition());
                } else if (elseStmt.isPresent() && isNodeInStatement(current, elseStmt.get())) {
                    conditions.add(BinaryOps.negateCondition(ifStmt.getCondition()));
                }
            }

            current = parent;
        }

        Collections.reverse(conditions);
        return conditions;
    }

    private static boolean isNodeInStatement(Node node, Statement stmt) {
        if (stmt == null) return false;
        if (stmt.equals(node)) return true;

        for (Node child : stmt.getChildNodes()) {
            if (child.equals(node) || isNodeDescendant(node, child)) {
                return true;
            }
        }
        return false;
    }


    private static boolean isNodeDescendant(Node target, Node potentialParent) {
        if (potentialParent.equals(target)) return true;
        for (Node child : potentialParent.getChildNodes()) {
            if (isNodeDescendant(target, child)) {
                return true;
            }
        }
        return false;
    }
}
