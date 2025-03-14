package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.function.Consumer;

public class ConsumerEvaluator<T> extends FPEvaluator<T> implements Consumer<T> {

    public ConsumerEvaluator(String className) {
        super(className);
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("Consumer")
                .setTypeArguments(
                        new WildcardType()
                );
    }

    @Override
    public void accept(T t) {

        try {
            if (methodDeclaration != null) {
                executeMethod(methodDeclaration);
                AntikytheraRunTime.push(new Variable(t));
            }
            method.invoke(object, t);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
