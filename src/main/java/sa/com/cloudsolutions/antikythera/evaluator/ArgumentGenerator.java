package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;

import java.util.HashMap;
import java.util.Map;

public abstract class ArgumentGenerator {
    protected Map<String, Variable> arguments = new HashMap<>();

    public abstract void generateArgument(Parameter param) throws ReflectiveOperationException;

    public void addExpression(String name, Expression expr) {
        arguments.put(name, new Variable(expr));
    }

    public Map<String, Variable> getArguments() {
        return arguments;
    }
}
