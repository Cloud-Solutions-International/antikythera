package sa.com.cloudsolutions.antikythera.exception;

import sa.com.cloudsolutions.antikythera.evaluator.Variable;

public class AUTException extends AntikytheraException{
    private transient Variable variable;

    public AUTException(String message) {
        super(message);
    }

    public AUTException(String message, Throwable cause) {
        super(message, cause);
    }

    public AUTException(Throwable cause) {
        super(cause);
    }

    public AUTException(String message, Variable v) {
        super(message);
        this.variable = v;
    }

    public Variable getVariable() {
        return variable;
    }

    public void setVariable(Variable v) {
        this.variable = v;
    }
}
