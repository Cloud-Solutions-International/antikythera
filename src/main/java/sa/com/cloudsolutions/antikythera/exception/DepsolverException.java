package sa.com.cloudsolutions.antikythera.exception;

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
