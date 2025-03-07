package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.Optional;

public class FunctionalEvaluator<T> extends Evaluator {
    private MethodDeclaration methodDeclaration;

    public FunctionalEvaluator(String className) {
        super(className);
    }

    public <T> void apply(T t) throws ReflectiveOperationException {
        AntikytheraRunTime.push(new Variable(t));
        executeMethod(methodDeclaration);
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
