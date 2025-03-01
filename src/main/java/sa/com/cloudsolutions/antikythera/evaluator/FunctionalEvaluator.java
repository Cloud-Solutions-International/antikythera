package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.Map;

public class FunctionalEvaluator extends AbstractEvaluator implements ExpressionEvaluator{
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

    @Override
    public Variable executeMethod(CallableDeclaration<?> cd) throws ReflectiveOperationException {
        return null;
    }
}
