package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.Constructor;


public class EvaluatorFactory {

    private EvaluatorFactory() {}

    public static Evaluator create(String className, Evaluator enclosure) {
        Context c = new Context(className, enclosure);

        CompilationUnit primary = AntikytheraRunTime.getCompilationUnit(className);
        String parent = className.substring(0, className.lastIndexOf('.'));
        CompilationUnit secondary = AntikytheraRunTime.getCompilationUnit(parent);

        if (primary != null && primary.equals(secondary)) {
            return EvaluatorFactory.create(c, InnerClassEvaluator.class);
        }

        if (enclosure instanceof SpringEvaluator) {
            return EvaluatorFactory.create(c, InnerClassEvaluator.class);
        }
        return EvaluatorFactory.create(c, Evaluator.class);
    }

    public static Evaluator create(String className) {
        return create(className, Evaluator.class);
    }

    public static <T extends Evaluator> T create(String className, Class<T> evaluatorType) {
        Context c = new Context(className);
        return create(c, evaluatorType);
    }

    private static <T extends Evaluator> T create(Context c, Class<T> evaluatorType) {
        Evaluator eval = createLazily(c, evaluatorType);
        if (eval.getCompilationUnit() != null) {
            eval.setupFields();
            eval.initializeFields();
        }
        return evaluatorType.cast(eval);
    }

    public static <T extends Evaluator> T createLazily(String className, Class<T> evaluatorType) {
        Context c = new Context(className);
        return createLazily(c, evaluatorType);
    }

    public static <T extends Evaluator> T createLazily(Context c , Class<T> evaluatorType) {
        try {
            Constructor<T> constructor = evaluatorType.getDeclaredConstructor(Context.class);
            Evaluator eval = constructor.newInstance(c);
            return evaluatorType.cast(eval);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    public static class Context {
        String className;
        Evaluator enclosure;

        private Context(String className) {
            this.className = className;
        }

        private Context(String className, Evaluator enclosure) {
            this(className);
            this.enclosure = enclosure;
        }

        public String getClassName() {
            return className;
        }

        public Evaluator getEnclosure() {
            return enclosure;
        }
    }
}
