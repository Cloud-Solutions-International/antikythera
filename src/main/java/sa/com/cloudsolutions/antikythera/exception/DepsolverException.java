package sa.com.cloudsolutions.antikythera.exception;

public class DepsolverException extends RuntimeException {

    public DepsolverException(String message) {
        super(message);
    }
    public DepsolverException(AntikytheraException e) {
        super(e);
    }
}
