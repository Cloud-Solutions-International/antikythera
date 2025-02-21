package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ArgumentGenerator {
    protected Map<String, Variable> arguments = new HashMap<>();
    protected boolean backTracking = false;
    /**
     * The preconditions that need to be met before the test can be executed.
     */
    private final List<Expression> preConditions = new ArrayList<>();

    public abstract void generateArgument(Parameter param) throws ReflectiveOperationException;

    public void addExpression(String name, Expression expr) {
        arguments.put(name, new Variable(expr));
    }

    public Map<String, Variable> getArguments() {
        return arguments;
    }

    public List<Expression> getPreConditions() {
        return preConditions;
    }

    public boolean isBackTracking() {
        return backTracking;
    }
}
