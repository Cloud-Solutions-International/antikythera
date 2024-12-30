package sa.com.cloudsolutions.antikythera.exception;

public class EvaluatorException extends AntikytheraException {
    private int error;
    public static final int NPE = 1;
    public static final int INTERNAL_SERVER_ERROR = 2;

    public EvaluatorException(String message) {
        super(message);
    }

    public EvaluatorException(String message, int error) {
        super(message);
        this.error = error;
    }

    public EvaluatorException(String message, Throwable cause) {
        super(message, cause);
    }

    public int getError() {
        return error;
    }

    public void setError(int error) {
        this.error = error;
    }
}
