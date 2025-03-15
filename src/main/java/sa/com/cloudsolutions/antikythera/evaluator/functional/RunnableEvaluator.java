package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

public class RunnableEvaluator extends FPEvaluator implements Runnable {
    public RunnableEvaluator() {
        this(RUNNABLE);
    }
    public RunnableEvaluator(String className) {
        super(className);
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("Runnable")
                .setTypeArguments(
                        new WildcardType()
                );
    }

    @Override
    public void run() {
        try {
            if (methodDeclaration != null) {
                executeMethod(methodDeclaration);
            }
            method.invoke(object);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }
}
