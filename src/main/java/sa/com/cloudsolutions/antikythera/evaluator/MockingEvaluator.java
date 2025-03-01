package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.Map;

public class MockingEvaluator implements ExpressionEvaluator {
    public MockingEvaluator() {

    }

    public MockingEvaluator(String fqdn) {

    }

    @Override
    public Variable evaluateExpression(Expression expr) throws ReflectiveOperationException {
        return null;
    }

    @Override
    public CompilationUnit getCompilationUnit() {
        return null;
    }

    @Override
    public void setCompilationUnit(CompilationUnit compilationUnit) {
    }

    @Override
    public void executeConstructor(CallableDeclaration<?> md) throws ReflectiveOperationException {
    }

    @Override
    public void setupFields(CompilationUnit cu) {
    }

    @Override
    public Map<String, Variable> getFields() {
        return null;
    }

    /**
     *
     * @param cd CallableDeclaration a method being executed.
     * @return null when the method defined by the callable declaration is of the type void.
     *   returns a reasonable value when the method is not void.
     * @throws ReflectiveOperationException
     */
    @Override
    public Variable executeMethod(CallableDeclaration<?> cd) throws ReflectiveOperationException {
        if (cd instanceof MethodDeclaration md) {
            Type returnType = md.getType();
            if (returnType.isVoidType()) {
                return null;
            }
            if (returnType.isPrimitiveType()) {
                return Reflect.variableFactory(returnType.toString());
            }
            if (cd.findCompilationUnit().isPresent()) {
                String fqdn = AbstractCompiler.findFullyQualifiedName(cd.findCompilationUnit().get(), returnType.toString());
                return Reflect.variableFactory(fqdn);
            }
        }
        return null;
    }
}
