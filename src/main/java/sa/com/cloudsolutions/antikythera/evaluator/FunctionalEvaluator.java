package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.Map;

public class FunctionalEvaluator extends AbstractEvaluator implements ExpressionEvaluator{
    private MethodDeclaration methodDeclaration;

    public FunctionalEvaluator(String className) {
        super(className);
    }

    @Override
    public Variable evaluateExpression(Expression expr) throws ReflectiveOperationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void executeConstructor(CallableDeclaration<?> md) throws ReflectiveOperationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setupFields(CompilationUnit cu) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Map<String, Variable> getFields() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setMethod(MethodDeclaration md) {
        this.methodDeclaration = md;
    }

    /**
     * Execute a method call.
     * @param wrapper the method call expression wrapped so that the argument types are available
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    @Override
    public Variable executeMethod(MCEWrapper wrapper) throws ReflectiveOperationException {
        returnFrom = null;

        Variable v = executeMethod(methodDeclaration);
        if (v != null && v.getValue() == null) {
            v.setType(methodDeclaration.getType());
        }
        return v;
    }
}
