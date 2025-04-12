package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReturnConditionVisitor extends VoidVisitorAdapter<Void> {
    private final List<Expression> conditions = new ArrayList<>();
    private final ReturnStmt targetReturn;

    public ReturnConditionVisitor(ReturnStmt returnStmt) {
        this.targetReturn = returnStmt;
    }
    @Override
    public void visit(ReturnStmt returnStmt, Void arg) {
        if (returnStmt.equals(targetReturn)) {
            Node current = returnStmt;
            boolean hasHandledReturn = false;

            while (!(current instanceof MethodDeclaration)) {
                if (current instanceof BlockStmt block) {
                    Node parent = block.getParentNode().orElse(null);
                    if (parent instanceof IfStmt ifStmt) {
                        hasHandledReturn = true;
                        if (ifStmt.getElseStmt().isPresent() &&
                            ifStmt.getElseStmt().get().equals(block)) {
                            conditions.add(negateCondition(ifStmt.getCondition()));
                        } else {
                            conditions.add(ifStmt.getCondition());
                        }
                    }
                }
                current = current.getParentNode().orElse(null);
                if (current == null) break;
            }

            if (!hasHandledReturn && current instanceof MethodDeclaration method) {
                method.findFirst(IfStmt.class).ifPresent(ifStmt ->
                    conditions.add(negateCondition(ifStmt.getCondition()))
                );
            }
        }
        super.visit(returnStmt, arg);
    }
    private Expression negateCondition(Expression condition) {
        if (condition instanceof BinaryExpr binaryExpr) {
            BinaryExpr.Operator newOp = switch (binaryExpr.getOperator()) {
                case EQUALS -> BinaryExpr.Operator.NOT_EQUALS;
                case NOT_EQUALS -> BinaryExpr.Operator.EQUALS;
                case GREATER -> BinaryExpr.Operator.LESS_EQUALS;
                case GREATER_EQUALS -> BinaryExpr.Operator.LESS;
                case LESS -> BinaryExpr.Operator.GREATER_EQUALS;
                case LESS_EQUALS -> BinaryExpr.Operator.GREATER;
                default -> null;
            };

            if (newOp != null) {
                return new BinaryExpr(binaryExpr.getLeft(), binaryExpr.getRight(), newOp);
            }
        }

        return new UnaryExpr(condition, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
    }

    public Expression getCombinedCondition() {
        if (conditions.isEmpty()) {
            return null;
        }

        Expression result = conditions.getFirst();
        for (int i = 1; i < conditions.size(); i++) {
            result = new BinaryExpr(
                    result,
                    conditions.get(i),
                    BinaryExpr.Operator.AND
            );
        }
        return result;
    }
}
