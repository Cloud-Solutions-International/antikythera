package sa.com.cloudsolutions.antikythera.exception;

public class EvaluatorException extends Exception{
    public EvaluatorException(String message) {
        super(message);
    }

    public EvaluatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
