package sa.com.cloudsolutions.antikythera.exception;

public class DepsolverException extends RuntimeException {
    public DepsolverException(AntikytheraException e) {
        super(e);
    }
}
