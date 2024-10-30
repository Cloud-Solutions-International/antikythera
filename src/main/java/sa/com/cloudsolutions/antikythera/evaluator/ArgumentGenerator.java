package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public abstract class ArgumentGenerator {
    public abstract Variable mockParameter(String typeName);
    public abstract void generateArgument(Parameter param) throws ReflectiveOperationException;
}
