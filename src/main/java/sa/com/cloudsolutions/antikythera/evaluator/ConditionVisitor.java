package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
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
    public void visit(IfStmt stmt, LineOfCode parent) {
        LineOfCode lineOfCode = new LineOfCode(stmt);
        lineOfCode.setParent(parent);
        if (canDriveCondition(lineOfCode, stmt.getCondition())) {
            Branching.add(lineOfCode);
        }

        // Visit the "then" branch
        stmt.getThenStmt().accept(this, lineOfCode);

        // Visit the "else" branch if it exists
        stmt.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, lineOfCode));
    }

    @Override
    public void visit(LambdaExpr n, LineOfCode arg) {
        // Do not traverse into lambda bodies — conditions inside lambdas belong to a different
        // callable scope and must not be registered as branches of the enclosing method.
    }

    @Override
    public void visit(ConditionalExpr expr, LineOfCode parent) {
        LineOfCode lineOfCode = new LineOfCode(expr.getCondition());
        if (canDriveCondition(lineOfCode, expr.getCondition())) {
            lineOfCode.setParent(parent);
            Branching.add(lineOfCode);
        }
    }

    private boolean canDriveCondition(LineOfCode lineOfCode, Expression condition) {
        CallableDeclaration<?> md = lineOfCode.getCallableDeclaration();
        NameCollector nameCollector = new NameCollector();

        condition.accept(nameCollector, null);
        Set<String> names = nameCollector.getNames();

        for (String name : names) {
            for (Parameter p : md.getParameters()) {
                if (p.getName().asString().equals(name)) {
                    return true;
                }
            }
            if (md.findAncestor(ClassOrInterfaceDeclaration.class)
                    .flatMap(cid -> cid.getFieldByName(name))
                    .isPresent()) {
                return true;
            }
        }

        return referencesLocalState(lineOfCode, names);
    }

    private boolean referencesLocalState(LineOfCode lineOfCode, Set<String> names) {
        CallableDeclaration<?> callable = lineOfCode.getCallableDeclaration();
        List<Statement> statements;
        if (callable instanceof MethodDeclaration md) {
            if (md.getBody().isEmpty()) return false;
            statements = md.getBody().orElseThrow().getStatements();
        } else if (callable instanceof ConstructorDeclaration cd) {
            statements = cd.getBody().getStatements();
        } else {
            return false;
        }

        Statement targetStatement = lineOfCode.getStatement();
        Set<String> localNames = new HashSet<>();
        for (Statement stmt : statements) {
            stmt.findAll(VariableDeclarationExpr.class).forEach(vde ->
                    vde.getVariables().forEach(v -> localNames.add(v.getNameAsString())));
            stmt.findAll(AssignExpr.class).forEach(assignExpr -> {
                if (assignExpr.getTarget().isNameExpr()) {
                    localNames.add(assignExpr.getTarget().asNameExpr().getNameAsString());
                }
            });
            if (stmt == targetStatement) {
                break;
            }
        }

        for (String name : names) {
            if (localNames.contains(name)) {
                return true;
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

        while (current != null && !(current instanceof CallableDeclaration)) {
            Optional<Node> parentNode = current.getParentNode();
            if (parentNode.isEmpty()) break;

            Node parent = parentNode.get();
            if (parent instanceof IfStmt ifStmt) {
                Statement thenStmt = ifStmt.getThenStmt();
                Optional<Statement> elseStmt = ifStmt.getElseStmt();

                // Check if this conditional is registered in Branching
                LineOfCode lineOfCode = Branching.get(ifStmt.hashCode());
                if (lineOfCode != null) {
                    if (isNodeInStatement(current, thenStmt)) {
                        conditions.add(ifStmt.getCondition());
                    } else if (elseStmt.isPresent() && isNodeInStatement(current, elseStmt.get())) {
                        conditions.add(BinaryOps.negateCondition(ifStmt.getCondition()));
                    }
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

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            if (n.getScope().isThisExpr()) {
                names.add(n.getNameAsString());
            }
            super.visit(n, arg);
        }

        public Set<String> getNames() {
            return names;
        }
    }
}
