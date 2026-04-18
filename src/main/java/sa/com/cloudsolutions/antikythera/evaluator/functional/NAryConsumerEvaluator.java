package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

/**
 * Evaluator for n-ary (3+ parameter) lambdas that return void.
 * Java has no standard functional interface beyond BiConsumer, so this evaluator
 * is not bound to a named interface. It is dispatched via FunctionalInvocationHandler
 * using the raw args array from the dynamic proxy invoke call.
 */
public class NAryConsumerEvaluator extends FPEvaluator<Object> {

    public NAryConsumerEvaluator(EvaluatorFactory.Context context) {
        super(context);
        this.enclosure = context.getEnclosure();
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType().setName(OBJECT_TYPE);
    }

    public void invoke(Object... args) {
        for (int i = args.length - 1; i >= 0; i--) {
            AntikytheraRunTime.push(new Variable(args[i]));
        }
        try {
            executeMethod(methodDeclaration);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }
}
