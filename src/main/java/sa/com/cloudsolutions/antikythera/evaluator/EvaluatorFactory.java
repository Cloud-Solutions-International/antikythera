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
     * Create an evaluator with the given context (eager mode).
     * <p>
     * If an auto-wired evaluator exists for the requested class, that instance is returned. Otherwise,
     * a new evaluator of the specified type is created, its fields are discovered via the compilation
     * unit, and dependent field evaluators are created and initialized.
     * </p>
     *
     * @param c the creation context wrapping the target class name and optional enclosing evaluator
     * @param evaluatorType the class of {@link Evaluator} (or one of its subclasses) to instantiate
     * @return an eagerly initialized evaluator instance
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

    /**
     * Attempt to retrieve an auto-wired evaluator for the target class in the given context.
     * <p>
     * If an auto-wired variable exists and holds an Evaluator instance, it is returned; otherwise,
     * {@code null} is returned. If the variable exists but does not hold an Evaluator, an
     * {@link sa.com.cloudsolutions.antikythera.exception.AntikytheraException} is thrown.
     * </p>
     *
     * @param c the creation context containing the target class name
     * @return an auto-wired evaluator instance if available; otherwise {@code null}
     */
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
     * <p>
     * In lazy mode, no evaluators will be created for dependent fields at this point; field discovery
     * and initialization are deferred.
     * </p>
     *
     * @param className the fully qualified name of the class for which an evaluator is being created
     * @param evaluatorType the class of {@link Evaluator} (or one of its subclasses) to instantiate
     * @return a lazily initialized evaluator instance
     */
    public static <T extends Evaluator> T createLazily(String className, Class<T> evaluatorType) {
        Context c = new Context(className);
        return createLazily(c, evaluatorType);
    }

    /**
     * Lazily create an evaluator using the provided context.
     * <p>
     * If an auto-wired evaluator exists for the requested class, that instance is returned. Otherwise,
     * a new evaluator of the specified type is created without triggering field discovery/initialization.
     * </p>
     *
     * @param c the creation context wrapping the target class name and optional enclosing evaluator
     * @param evaluatorType the class of {@link Evaluator} (or one of its subclasses) to instantiate
     * @return a lazily initialized evaluator instance
     */
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
