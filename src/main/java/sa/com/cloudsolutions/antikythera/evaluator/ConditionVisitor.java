package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ConditionVisitor extends VoidVisitorAdapter<LineOfCode> {

    @Override
    @SuppressWarnings("unchecked")
    public void visit(IfStmt stmt, LineOfCode parent) {
        stmt.findAncestor(MethodDeclaration.class).ifPresent(methodDeclaration -> {
            if (canMatchParameters(methodDeclaration, stmt.getCondition())) {
                LineOfCode lineOfCode = new LineOfCode(stmt);
                lineOfCode.setParent(parent);
                Branching.add(lineOfCode);

                // Visit the "then" branch
                stmt.getThenStmt().accept(this, lineOfCode);

                // Visit the "else" branch if it exists
                stmt.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, lineOfCode));
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void visit(ConditionalExpr expr, LineOfCode parent) {
        expr.findAncestor(MethodDeclaration.class).ifPresent(methodDeclaration -> {
            expr.findAncestor(Statement.class).ifPresent(statement -> {
                LineOfCode lineOfCode = new LineOfCode(statement);
                lineOfCode.setParent(parent);
                Branching.add(lineOfCode);
            });
        });
    }

    /**
     * Determines if the method parameters are directly connected to the condition
     * Note due to a bug in java parser, accepting md as a parameter rather than
     * determining it here. findAncestor does not seem to be working on Conditional expression
     * as of May 3, 2025
     * @param md the method declaration in which to check for the parameters and conditionals match
     * @param condition the condition to check
     * @return true if the parameters and conditionals match up
     */
    private boolean canMatchParameters(MethodDeclaration md, Expression condition) {
        NameCollector nameCollector = new NameCollector();

        Set<String> names = nameCollector.getNames();
        condition.accept(nameCollector, null);

        for (String name : names) {
            for (Parameter p : md.getParameters()) {
                if (p.getName().asString().equals(name)) {
                    return true;
                }
            }
        }

        return false;
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

    public static boolean isNodeInStatement(Node node, Statement stmt) {
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


    private static class NameCollector extends VoidVisitorAdapter<Void> {
        private final Set<String> names = new HashSet<>();

        @Override
        public void visit(NameExpr n, Void arg) {
            names.add(n.getNameAsString());
            super.visit(n, arg);
        }

        public Set<String> getNames() {
            return names;
        }
    }
}
