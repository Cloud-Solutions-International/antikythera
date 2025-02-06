package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import java.util.HashMap;
import java.util.Map;

public abstract class ArgumentGenerator {
    protected Map<String, Variable> arguments = new HashMap<>();

    public abstract Variable mockParameter(Parameter param);
    public abstract void generateArgument(Parameter param) throws ReflectiveOperationException;

    public Map<String, Variable> getArguments() {
        return arguments;
    }
}
