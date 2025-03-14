package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;

public class RunnableEvaluator extends FPEvaluator implements Runnable {

    public RunnableEvaluator(String className) {
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
    public void run() {
        try {
            executeMethod(methodDeclaration);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
