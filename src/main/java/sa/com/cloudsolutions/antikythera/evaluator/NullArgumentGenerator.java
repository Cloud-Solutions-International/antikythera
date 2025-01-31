package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;

public class NullArgumentGenerator extends ArgumentGenerator{
    @Override
    public Variable mockParameter(Parameter typeName) {
        return null;
    }

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        Variable variable = new Variable(null);
        variable.setType(param.getType());
        arguments.put(param.getNameAsString(), variable);
        AntikytheraRunTime.push(variable);
    }
}
