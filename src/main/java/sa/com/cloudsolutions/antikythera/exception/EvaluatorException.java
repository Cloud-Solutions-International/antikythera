package sa.com.cloudsolutions.antikythera.exception;

import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;

public class EvaluatorException extends AntikytheraException {
    private int error;
    public static final int NPE = 1;
    public static final int INTERNAL_SERVER_ERROR = 2;

    public EvaluatorException(Expression left, Expression right) {
        super("Could not perform binary operation on " + left + " and " + right);
    }
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
