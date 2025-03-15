package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;


import java.util.function.Function;

public class FunctionEvaluator<T,R> extends FPEvaluator<T> implements Function<T,R> {
    public FunctionEvaluator() {
        this(FUNCTION);
    }
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

        try {
            if (methodDeclaration != null) {
                AntikytheraRunTime.push(new Variable(t));
                Variable v = executeMethod(methodDeclaration);
                return (R) v.getValue();
            }
            return (R) method.invoke(object, t);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }
}
