package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.mockito.Mockito;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import java.util.List;
import java.util.Optional;

public class TestSuiteEvaluator extends Evaluator {
    public TestSuiteEvaluator(CompilationUnit cu, String className) {
        super();
        this.className = className;
        this.cu = cu;
    }

    protected void executeBlock(List<Statement> statements) throws ReflectiveOperationException {
        try {
            for (Statement stmt : statements) {
                if(loops.isEmpty() || loops.peekLast().equals(Boolean.TRUE)) {
                    if (stmt instanceof ExpressionStmt expressionStmt
                            && expressionStmt.getExpression() instanceof MethodCallExpr methodCallExpr) {
                        if (checkForMockingCalls(stmt, methodCallExpr)) continue;
                    }
                    executeStatement(stmt);
                    if (returnFrom != null) {
                        break;
                    }
                }
            }
        } catch (EvaluatorException | ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    private boolean checkForMockingCalls(Statement stmt, MethodCallExpr methodCallExpr) throws ReflectiveOperationException {
        Optional<Expression> scope = methodCallExpr.getScope();
        if (scope.isPresent()) {
            Expression expr = scope.get();
            if(expr.isMethodCallExpr()) {
                MethodCallExpr mce = scope.get().asMethodCallExpr();
                if (mce.getNameAsString().equals("when")) {
                    Expression when = mce.getArguments().getFirst().orElseThrow();
                    if (when instanceof MethodCallExpr whenArgument && whenArgument.getScope().isPresent()) {
                        Expression name = whenArgument.getScope().get();
                        if (name instanceof NameExpr nameExpr) {
                            Variable found = getValue(stmt, nameExpr.getNameAsString());
                            if (found != null) {
                                Class<?> clazz = found.getClazz();
                                if (clazz != null) {
                                    MockingRegistry.when(className, null, null);
                                }
                            }
                        }
                    }
                    Variable t = evaluateExpression(methodCallExpr.getArguments().getFirst().orElseThrow());

                    return true;
                }
            }
            else if (expr.isFieldAccessExpr()) {
                FieldAccessExpr fce = scope.get().asFieldAccessExpr();
                if (fce.getNameAsString().equals("Mockito")) {

                }
            }
        }
        return false;
    }
}
