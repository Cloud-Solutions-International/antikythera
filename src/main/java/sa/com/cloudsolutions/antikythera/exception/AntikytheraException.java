package sa.com.cloudsolutions.antikythera.exception;

/**
 * Base unchecked exception for all errors originating within the Antikythera engine itself.
 * Subclasses distinguish between evaluator errors ({@link EvaluatorException}),
 * application-under-test errors ({@link AUTException}), and generator errors
 * ({@link GeneratorException}).
 */
public class AntikytheraException  extends RuntimeException {

    public AntikytheraException(String message) {
        super(message);
    }

    public AntikytheraException(String message, Throwable cause) {
        super(message, cause);
    }

    public AntikytheraException(Throwable cause) {
        super(cause);
    }
}
