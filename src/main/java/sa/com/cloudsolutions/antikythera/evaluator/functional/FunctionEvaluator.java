package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;


import java.util.function.Function;

public class FunctionEvaluator<T,R> extends FPEvaluator implements Function<T,R> {
    public FunctionEvaluator(String className) {
        super(className);
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("Function")
                .setTypeArguments(
                        new WildcardType(), new WildcardType()
                );
    }

    @Override
    public R apply(T t) {
        AntikytheraRunTime.push(new Variable(t));
        try {
            Variable v = executeMethod(methodDeclaration);
            return (R) v.getValue();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
