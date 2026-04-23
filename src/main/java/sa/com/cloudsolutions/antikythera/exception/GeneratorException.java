package sa.com.cloudsolutions.antikythera.exception;

/**
 * Thrown when the test generation pipeline encounters an error, such as failures during
 * code emission, template processing, or assertion construction.
 */
public class GeneratorException extends RuntimeException {

    public GeneratorException(String message) {
        super(message);
    }

    public GeneratorException(Throwable cause) {
        super(cause);
    }

    public GeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
