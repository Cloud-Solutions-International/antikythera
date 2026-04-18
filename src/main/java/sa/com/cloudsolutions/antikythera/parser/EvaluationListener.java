package sa.com.cloudsolutions.antikythera.parser;

/**
 * Callback interface used by {@link DepsolvingParser} to notify interested parties
 * when a method evaluation fails.
 */
public interface EvaluationListener {
    /**
     * Called when an exception is caught while evaluating a callable.
     *
     * @param error the exception message (may be null)
     */
    void onMethodFailed(String error);
}
