package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.CallableDeclaration;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;

import java.io.IOException;
import java.util.List;

/**
 * Interface that decouples the core SpringEvaluator from concrete test generator implementations.
 * Concrete implementations live in antikythera-test-generator.
 */
public interface ITestGenerator {
    void setPreConditions(List<Precondition> preConditions);
    void createTests(CallableDeclaration<?> md, MethodResponse response);
    void setArgumentGenerator(ArgumentGenerator argumentGenerator);
    void save() throws IOException;
}
