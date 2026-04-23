package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;

/**
 * Argument generator that always produces {@code null} values, used to test
 * null-handling paths during symbolic execution.
 */
public class NullArgumentGenerator extends ArgumentGenerator{

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        Variable variable = new Variable(null);
        variable.setType(param.getType());
        arguments.put(param.getNameAsString(), variable);
        AntikytheraRunTime.push(variable);
    }
}
