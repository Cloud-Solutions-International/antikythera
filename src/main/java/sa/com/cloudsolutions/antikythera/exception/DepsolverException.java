package sa.com.cloudsolutions.antikythera.exception;

/**
 * Thrown when the dependency solver encounters an error while resolving or extracting
 * class, field, or method dependencies from the source AST.
 */
public class DepsolverException extends RuntimeException {

    public DepsolverException(String message) {
        super(message);
    }

    public DepsolverException(Throwable cause) {
        super(cause);
    }

    public DepsolverException(AntikytheraException e) {
        super(e);
    }
}
