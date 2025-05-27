package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.Constructor;


public class EvaluatorFactory {

    private EvaluatorFactory() {}

    /**
     * Eagerly create an evaluator.
     * When eager mode is used, all the fields will be explored and evaluators will be created for
     * all the fields that need one.
     * This method delegates the work to the other create method.
     * @param className the name of the class for which we are creating an evaluator
     * @param enclosure another evaluator instance which encloses this evaluator. As an example It
     *                 maybe the evaluator for the classes that holds a field.
     * @return an evaluator instance.
     */
    public static Evaluator create(String className, Evaluator enclosure) {
        Context c = new Context(className, enclosure);

        CompilationUnit primary = AntikytheraRunTime.getCompilationUnit(className);
        String parent = className.substring(0, className.lastIndexOf('.'));
        CompilationUnit secondary = AntikytheraRunTime.getCompilationUnit(parent);

        if (primary != null && primary.equals(secondary)) {
            return EvaluatorFactory.create(c, InnerClassEvaluator.class);
        }

        return EvaluatorFactory.create(c, enclosure.getClass());
    }

    /**
     * Eagerly create an evaluator.
     * @param className the class for which we are creating an evaluator
     * @param evaluatorType an instance of Evaluator or one of it's subclasses
     * @return an evaluator instance.
     */
    public static <T extends Evaluator> T create(String className, Class<T> evaluatorType) {
        Context c = new Context(className);
        return create(c, evaluatorType);
    }

    /**
     * Create an evaluator with the given context
     * @param c the context instance which wraps a class name and an enclosing evaluator
     * @param evaluatorType  an instance of Evaluator or one of it's subclasses
     * @return an evaluator instance.
     */
    private static <T extends Evaluator> T create(Context c, Class<T> evaluatorType) {
        Evaluator autoWired = findAutoWire(c);
        if (autoWired != null) {
            return evaluatorType.cast(autoWired);
        }

        Evaluator eval = createLazily(c, evaluatorType);
        if (eval.getCompilationUnit() != null) {
            eval.setupFields();
            eval.initializeFields();
        }
        return evaluatorType.cast(eval);
    }

    private static Evaluator findAutoWire(Context c) {
        Variable v = AntikytheraRunTime.getAutoWire(c.getClassName());
        if (v != null) {
            if (v.getValue() instanceof Evaluator eval) {
                return eval;
            }
            throw new AntikytheraException("Illegal state for auto wire.");
        }
        return null;
    }

    /**
     * Lazily create an evaluator instance.
     * In lazy mode no evaluators will be immediately created for any fields that need it.
     * @param className the name of the class for which an evaluator is being created
     * @param evaluatorType  an instance of Evaluator or one of it's subclasses
     * @return an evaluator instance.
     */
    public static <T extends Evaluator> T createLazily(String className, Class<T> evaluatorType) {
        Context c = new Context(className);
        return createLazily(c, evaluatorType);
    }

    public static <T extends Evaluator> T createLazily(Context c , Class<T> evaluatorType) {
        Evaluator autoWired = findAutoWire(c);
        if (autoWired != null) {
            return evaluatorType.cast(autoWired);
        }

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

        @Override
        public String toString() {
            if (className != null) {
                return className;
            }
            return super.toString();
        }
    }
}
