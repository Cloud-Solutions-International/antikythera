package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public abstract class ArgumentGenerator {
    protected Map<String, Variable> arguments = new HashMap<>();

    public abstract Variable mockParameter(String typeName);
    public abstract void generateArgument(Parameter param) throws ReflectiveOperationException;

    public Map<String, Variable> getArguments() {
        return arguments;
    }
}
