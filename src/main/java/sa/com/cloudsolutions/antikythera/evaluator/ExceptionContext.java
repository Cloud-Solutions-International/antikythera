package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete context information captured when an exception occurs during evaluation.
 * This enables intelligent test generation by understanding WHY and WHERE exceptions occur.
 */
public class ExceptionContext {
    private Exception exception;                           // The actual exception
    private List<ConditionalExpr> pathConditions;         // Branching conditions leading to exception
    private Map<String, Variable> argumentStates;          // Method argument values at exception time
    private Statement throwLocation;                       // AST node where exception thrown
    private boolean insideLoop;                            // Was exception inside iteration?
    private LoopContext loopContext;                       // Details if inside loop
    private long timestamp;                                // When exception occurred (for ordering)

    public ExceptionContext() {
        this.pathConditions = new ArrayList<>();
        this.argumentStates = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public List<ConditionalExpr> getPathConditions() {
        return pathConditions;
    }

    public void setPathConditions(List<ConditionalExpr> pathConditions) {
        this.pathConditions = pathConditions != null ? pathConditions : new ArrayList<>();
    }

    public Map<String, Variable> getArgumentStates() {
        return argumentStates;
    }

    public void setArgumentStates(Map<String, Variable> argumentStates) {
        this.argumentStates = argumentStates != null ? argumentStates : new HashMap<>();
    }

    public Statement getThrowLocation() {
        return throwLocation;
    }

    public void setThrowLocation(Statement throwLocation) {
        this.throwLocation = throwLocation;
    }

    public boolean isInsideLoop() {
        return insideLoop;
    }

    public void setInsideLoop(boolean insideLoop) {
        this.insideLoop = insideLoop;
    }

    public LoopContext getLoopContext() {
        return loopContext;
    }

    public void setLoopContext(LoopContext loopContext) {
        this.loopContext = loopContext;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ExceptionContext{" +
                "exception=" + (exception != null ? exception.getClass().getSimpleName() : "null") +
                ", pathConditions=" + pathConditions.size() +
                ", argumentStates=" + argumentStates.size() +
                ", insideLoop=" + insideLoop +
                ", loopContext=" + loopContext +
                '}';
    }
}
