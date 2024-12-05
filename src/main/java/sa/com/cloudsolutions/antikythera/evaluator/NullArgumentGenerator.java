package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;

public class NullArgumentGenerator extends ArgumentGenerator{
    @Override
    public Variable mockParameter(String typeName) {
        return null;
    }

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        AntikytheraRunTime.push(new Variable(null));
    }
}
