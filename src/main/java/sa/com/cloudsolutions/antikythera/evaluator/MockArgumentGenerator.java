package sa.com.cloudsolutions.antikythera.evaluator;

import org.mockito.Mockito;
import com.github.javaparser.ast.body.Parameter;

public class MockArgumentGenerator extends ArgumentGenerator {

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        Variable variable = new Variable(null);
        Mockito.mock(param.getType().asClassOrInterfaceType().getNameAsString());

        variable.setType(param.getType());
        arguments.put(param.getNameAsString(), variable);
        AntikytheraRunTime.push(variable);
    }
}
