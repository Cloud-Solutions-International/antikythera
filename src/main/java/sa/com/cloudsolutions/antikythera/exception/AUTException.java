package sa.com.cloudsolutions.antikythera.exception;

public class AUTException extends AntikytheraException{
    public AUTException(String message) {
        super(message);
    }

    public AUTException(String message, Throwable cause) {
        super(message, cause);
    }

    public AUTException(Throwable cause) {
        super(cause);
    }
}
