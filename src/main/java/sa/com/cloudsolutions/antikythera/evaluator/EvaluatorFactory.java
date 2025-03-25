package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.Constructor;

public class EvaluatorFactory {
    private EvaluatorFactory() {}

    public static Evaluator create(String className, Evaluator enclosure) {
        CompilationUnit primary = AntikytheraRunTime.getCompilationUnit(className);
        String parent = className.substring(0, className.lastIndexOf('.'));
        CompilationUnit secondary = AntikytheraRunTime.getCompilationUnit(parent);

        if (primary != null && primary.equals(secondary)) {
            InnerClassEvaluator eval = EvaluatorFactory.create(className, InnerClassEvaluator.class);
            eval.setEnclosure(enclosure);
            return eval;
        }

        if (enclosure instanceof SpringEvaluator) {
            return EvaluatorFactory.create(className, SpringEvaluator.class);
        }
        return EvaluatorFactory.create(className, Evaluator.class);
    }

    public static Evaluator create(String className) {
        return create(className, Evaluator.class);
    }

    public static <T extends Evaluator> T create(String className, Class<T> evaluatorType) {
        try {
            Constructor<?> cons = evaluatorType.getDeclaredConstructor();
            Evaluator eval = (Evaluator) cons.newInstance();
            eval.initialize(className, false);
            return evaluatorType.cast(eval);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    public static <T extends Evaluator> T createLazily(String className, Class<T> evaluatorType) {
        try {
            Constructor<?> cons = evaluatorType.getDeclaredConstructor();
            Evaluator eval = (Evaluator) cons.newInstance();
            eval.initialize(className, true);
            return evaluatorType.cast(eval);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }
}
