package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;

public class MethodInterceptor {
    private final Evaluator evaluator;

    public MethodInterceptor(Evaluator evaluator) {
        this.evaluator = evaluator;
    }


    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
        // Find the matching source method in the compilation unit
        String methodName = method.getName();

        // Get the compilation unit from the evaluator
        CompilationUnit cu = evaluator.getCompilationUnit();

        // Find the method declaration that matches this bytecode method
        MethodDeclaration methodDecl = cu.findFirst(MethodDeclaration.class,
            m -> m.getNameAsString().equals(methodName) &&
                 m.getParameters().size() == args.length).orElse(null);

        if (methodDecl != null) {
            // Push arguments onto stack in reverse order (as expected by evaluator)
            for (int i = args.length - 1; i >= 0; i--) {
                AntikytheraRunTime.push(new Variable(args[i]));
            }

            // Execute the method using source code evaluation
            Variable result = evaluator.executeMethod(methodDecl);

            // Return the actual value from the Variable wrapper
            return result != null ? result.getValue() : null;
        }

        return null;
    }


    public Evaluator getEvaluator() {
        return evaluator;
    }
}
