package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.body.MethodDeclaration;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

public class FunctionalEvaluator<T> extends Evaluator {
    protected MethodDeclaration methodDeclaration;

    public FunctionalEvaluator(String className) {
        super(className);
    }

    public void setMethod(MethodDeclaration methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
    }

    public Variable executeMethod(MCEWrapper wrapper) throws ReflectiveOperationException {
        returnFrom = null;
        if (wrapper.getMethodName().equals("apply")) {
            return executeMethod(methodDeclaration);
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
